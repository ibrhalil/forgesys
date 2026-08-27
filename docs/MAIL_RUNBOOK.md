# Mail Runbook — Kendi VPS'inde Giden Mail (K-53)

> Test aşaması senaryosu: ForgeSys bir süre **mevcut domain'in bir subdomain'i** altında
> çalışacak (ör. `app.example.com`), mail de **aynı domain'in başka bir VPS'indeki**
> kendi mail sunucunuzdan gidecek. Yönetilen sağlayıcı (Brevo vb.) alternatifi sonda.

## 0. Ön kontroller

- [ ] **Outbound port 25 açık mı?** (birçok VPS sağlayıcısı default kapalı tutar, ticket ile açılır)
  ```bash
  # VPS üzerinde:
  openssl s_client -connect smtp.gmail.com:25 -starttls smtp </dev/null | head -5
  # ya da: timeout 10 bash -c 'cat < /dev/null > /dev/tcp/smtp.gmail.com/25' && echo OPEN
  ```
- [ ] VPS IP'si karalistede mi? https://mxtoolbox.com/blacklists.aspx — temiz değilse o IP'yi kullanma.
- [ ] Domain'inizin DNS paneline erişim (A/TXT kayıtları) + VPS paneline erişim (PTR kaydı).

## 1. Ayrı bir mail subdomain'i seçin (öneri: `mg.<domain>`)

Mevcut domain'de zaten mail akışı olabilir (diğer servisler). Yeni kurulum **izole** olsun —
hep `mg.example.com` örneğiyle:

| Kayıt | Tür | Değer | Amaç |
|-------|-----|-------|------|
| `mg.example.com` | A | `<VPS_IP>` | Mail sunucu hostname'i |
| PTR / rDNS (VPS panelinden) | — | `mg.example.com` | **Kritik**: IP → hostname eşleşmesi; Gmail/Outlook ilk baktığı yer |
| `mg.example.com` | TXT (SPF) | `v=spf1 a:mg.example.com ip4:<VPS_IP> ~all` | Bu subdomain'den kim mail gönderebilir |
| `forgesys._domainkey.mg.example.com` | TXT (DKIM) | kurulumun ürettiği public key | İmza doğrulama (selector: `forgesys`) |
| `_dmarc.mg.example.com` | TXT (DMARC) | `v=DMARC1; p=none; rua=mailto:postmaster@example.com` | Politika izleme — ilk aşamada `p=none` |

> `MAIL_FROM=ForgeSys <no-reply@mg.example.com>` — From domain'i ile DKIM `d=` alanı aynı
> olduğu için alignment tamamdır. `p=none` ile başlayıp mail-tester skoru stabil ≥ 8 olunca
> `quarantine`/`reject`'e geçilir.

## 2. Mail sunucusu (VPS üzerinde)

İki eşdeğer yol; seçim size ait:

- **Hafif yol — düz Postfix** (sadece relay, kutu yok): Postfix + OpenDKIM (veya rspamd).
  `myhostname = mg.example.com`, ForgeSys'e SMTP auth için bir SASL kullanıcı açın.
- **Paket yol — docker-mailserver**: tek container, DKIM key üretimi dahil
  (`docker compose exec mail setup config dkim` → public key'i DNS'e koyun).

Kurulum sonrası zorunlu satır: **587 portuna SMTP AUTH + STARTTLS** (ForgeSys
`MAIL_SMTP_AUTH=true` + `MAIL_SMTP_STARTTLS=true` ile bağlanır).

## 3. Doğrulama (mail-tester)

1. https://www.mail-tester.com adresindeki adresi kopyalayın.
2. ForgeSys'ten oraya bir mail gönderin — en pratik yol: platform konsolu
   `/platform/mail` → **Test gönder** (`POST /api/v1/platform/mail/test-send`,
   `platform:mail:test` yetkisi) veya bir kullanıcının `forgot-password` akışı.
3. Skor ≥ 8 ve "SPF pass + DKIM pass + DMARC pass" görünüyor olmalı. Değilse en sık
   sebep: PTR yok/uyumsuz, SPF'te IP eksik, DKIM key'i DNS'e yanlış kopyalanmış.

## 4. ForgeSys tarafı — sadece ortam değişkenleri (kod değişikliği yok)

### Lokal dev'den gerçek gönderim (deneme için)

```bash
docker compose up -d                       # db + redis (+ mailpit, kullanılmaz)
MAIL_HOST=mg.example.com MAIL_PORT=587 \
MAIL_USERNAME=forgesys MAIL_PASSWORD=**** \
MAIL_SMTP_AUTH=true MAIL_SMTP_STARTTLS=true \
SPRING_PROFILES_ACTIVE=dev,smtp \
./mvnw -pl backend spring-boot:run
```

Mailpit'te denemek isterseniz env vermeden `dev,smtp` yeterli (default `localhost:1025`,
UI: http://localhost:8025).

### Prod deploy'da (docker-compose-prod)

Sunucudaki `.env`:

```env
MAIL_HOST=mg.example.com
MAIL_PORT=587
MAIL_USERNAME=forgesys
MAIL_PASSWORD=****
MAIL_FROM=ForgeSys <no-reply@mg.example.com>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true

# App-as-subdomain (test dönemi):
BASE_DOMAIN=app.example.com
APP_BASE_URL=https://app.example.com
```

- `BASE_DOMAIN` → `TenantFilter` tenant subdomain'lerini `acme.app.example.com` biçiminde çözer.
- `APP_BASE_URL` → mail linkleri `https://acme.app.example.com/verify-email?token=...` üretir.
- **Wildcard DNS**: `*.app.example.com` → app sunucusu. **Wildcard TLS** (K-33 Nginx
  gateway + wildcard sertifika) deploy edene kadar tarayıcıda sertifika uyarısı normaldir;
  API/mail akışı TLS sonlandırması gateway'e gelinceye kadar bu şekilde kalır.
- Boş `MAIL_HOST` ile app **bilinçli olarak açılmaz** (fail-fast — sessiz mail kaybı yok).

## 5. Alternatif: yönetilen sağlayıcı (daha az iş, daha iyi ilk deliverability)

Tek bir VPS IP'si yeni/isınmamış olduğu için Gmail ilk günlerde spam'e atabilir. Daha garantili
yol: aynı domain (`mg.example.com`) ile **Brevo** (ücretsiz 300 mail/gün) hesabı — domain
doğrulama DNS kayıtlarını Brevo verir, ForgeSys tarafında yalnızca `MAIL_HOST` vb. Brevo
SMTP değerleri olur. Kendi Postfix'inizle yönetilen sağlayıcı arasında geçiş = sadece `.env`.

## Bakım notları

- DKIM key rotasyonu (6-12 ay) ve DMARC raporlarını (`rua`) düzenli izleyin.
- Bounce yüzdesi yüksek olursa (eski adresler) gönderimi durdurup listeyi temizleyin —
  IP itibarı hızlı düşer, yavaş toparlanır.
- Mailpit **yalnızca dev** aracıdır; prod'da kullanılmaz (mailler gerçeğe gitmez).
