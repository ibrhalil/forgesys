# BACKLOG.md Güncellemeleri (K-21, 2026-07-20)

> Bu dosya BACKLOG.md'ye uygulanacak değişiklikleri içerir. BACKLOG.md çok büyük olduğu için write_file parser'ı tamamını yazamadı. Aşağıdaki 4 değişikliği BACKLOG.md'ye elle uygula, sonra bu dosyayı sil.

## Değişiklik 1 — Epic 1.5.A başlığına not ekle

BACKLOG.md'de `### Epic 1.5.A — @Transactional Fix` başlığının hemen altına, tablodan önce şu notu ekle:

```markdown
> **Not (2026-07-20, K-21):** Bu epic SF-101 (Epic 2.0.C) içinde çözülür — `TenantProvisioningService` refactor'ünün parçası olarak hem `createPendingCompany` hem `verifyAndProvision` `@Transactional` olacak. SF-001/SF-002 doğrudan SF-101 ile kapatılır; SF-003/SF-004 helper metotlar ve rollback test'i SF-101 sonrası SF-103 test kapsamında değerlendirilir.
```

Ayrıca aynı tabloda SF-001 ve SF-002'nin `Durum` sütununu `⬜` yerine `⬜ (SF-101 ile)` yap.

## Değişiklik 2 — Epic 2.0.C ekle (Epic 2.0.B'den sonra, Epic 2.1'den önce)

`### Epic 2.1 — MapStruct + DTO` başlığının hemen üstüne (yani `### Epic 2.0.B` tablosunun bittiği yerden sonra) şu yeni bölümü yapıştır:

```markdown
### Epic 2.0.C — Hibrit Tenant Signup Verification (K-21, 2026-07-20)

> **Karar (K-21):** Tenant signup bootstrap problem çözümü — Seçenek 3 (hibrit). Self-service signup `PROVISIONING` yaratır (şema/migration YOK, hafif); admin email verify linki tıklanınca `verify` endpoint'i SENKRON olarak `ACTIVE`'e çeker + schema + Flyway + admin user yaratır. **Polling/event yok** — tetikleyici kullanıcının linke tıklaması (HTTP request).

> **Neden auth'tan ÖNCE:** Mevcut `provisionTenant` open endpoint + ağır DDL (schema CREATE + Flyway) + subdomain/emailDomain squatting'e karşı korumasız. RBAC/auth kurulmadan önce signup yolunu sağlamlaştırıyoruz. Aynı zamanda SF-001/SF-002 `@Transactional` epic'ini de kapsar (refactor sırasında uygulanır).

> **Test stratejisi (K-21):** `VerificationSender` interface + profile bazlı impl'ler — `test` profilde `InMemoryVerificationSender` (token `ConcurrentHashMap`'te, test assertion), `dev`'de `LogVerificationSender` (INFO log'a link), `prod`'da `MailVerificationSender`. Test'ler H2'de çalışır (MailHog/Docker gerektirmez). `spring-boot-starter-mail` sadece prod impl ile eklenir (SF-105'e kadar ertelenir).

> **Scope ayrımı:** Bu epic tenant **signup** admin email doğrulaması (tenant bağlamı yok, `public` şema). SF-145 (Epic 2.9) tenant **içi** mevcut user email doğrulamasıdır (tenant şeması, `User.emailVerificationToken` kullanır). İkisi ayrı.

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-099 | P0 | M | feat | `TenantVerificationToken` entity (`public` şema, `GeneratedIdAuditEntity`) + `public/V2__tenant_verification_tokens.sql` migration + `TenantVerificationTokenRepository` | 178 | ⬜ |
| SF-100 | P1 | M | feat | `VerificationSender` interface + `InMemoryVerificationSender` (`@Profile("test")`) + `LogVerificationSender` (`@Profile("dev")`) — **mail dep YOK** | — | ⬜ |
| SF-101 | P0 | L | refactor | `TenantProvisioningService` böl: `createPendingCompany()` (PROVISIONING, `@Transactional`, SF-001 kapsar) + `verifyAndProvision(token)` (ACTIVE, `@Transactional`, SF-002 kapsar). Senkron PROVISIONING→ACTIVE | 099,100 | ⬜ |
| SF-102 | P0 | M | feat | DTO (`CompanyRegisterResponse`, `CompanyVerifyRequest`, `CompanyVerifyResponse`) + `AuthController` refactor: `register` → PROVISIONING döner + `POST /api/v1/auth/company/verify` endpoint | 101 | ⬜ |
| SF-103 | P1 | M | test | `TenantProvisioningServiceTest` (Mockito + `InMemoryVerificationSender` ile register→verify senkron akış + token expire + zaten-used senaryoları) | 102 | ⬜ |
| SF-104 | P3 | S | feat | **(Opsiyonel)** Scheduled cleanup job — expired `TenantVerificationToken` + bağlı PROVISIONING Company'leri sil (squatting/DB şişmesi önlem). Low-priority, kullanıcı tıklamamış token'lar için | 101 | ⬜ |
| SF-105 | P2 | M | feat | **(Ertelendi, Faz 5)** `MailVerificationSender` (`@Profile("prod")`) + `spring-boot-starter-mail` pom'a + `application-prod.yaml` SMTP config (sağlayıcı kararı: SMTP vs SendGrid vs SES) | 100 | ⬜ |
```

## Değişiklik 3 — SF-145 satırına not ekle

Epic 2.9 tablosunda SF-145 satırının `Title` sütununu şu hale getir:

```markdown
Email doğrulama akışı (`POST /auth/verify-email` token üret + `/confirm`) — tenant **içi** mevcut user doğrulaması. **Not (K-21):** Epic 2.0.C'den ayrı (orada tenant signup admin verify, `public` şema; burada `User.emailVerificationToken` kullanır)
```

## Değişiklik 4 — İstatistik bölümüne Epic 2.0.C satırı ekle

`### Faz 1.5-2 istatistik` tablosuna, `2.0.B Critical Fixes` satırından sonra şu satırı ekle:

```markdown
| 2.0.C Hibrit Tenant Signup Verification | 7 | 3 | 1 | 0 |
```

Ve `**Toplam**` satırını güncelle:
- Ticket: `93 (+1 ❌)` → `100 (+1 ❌)`
- P0: `29` → `32`

`> **Faz 1.5 (1.5.A-E, 20 ticket) ERTELENDİ**` notunun altındaki "aktif Faz 2 ticket sayısı: 93 − 20 = **73**" satırını da `100 − 20 = **80**` yap.

En alttaki `**Toplam (Faz 1.5-6):**` satırını da güncelle:
- `93 + 72 = **165 ticket**, **37 P0**, **31 L**.` → `100 + 72 = **172 ticket**, **40 P0**, **32 L**.`

## Kritik Yol güncellemesi

`### Kritik Yol (P0 zinciri — K-18)` bölümünde **Faz 2** satırına, `SF-167` ile `SF-178/179/180` arasına SF-099/SF-101/SF-102 zincirini ekle:

```markdown
**Faz 2:** SF-167 (DateTimeProvider bug) → SF-163/164 (error altyapısı) → **SF-099/101/102 (tenant signup verification, K-21)** → **SF-178/179/180 (multi-tenancy/UNIQUE/hashCode fixes)** → SF-071 → ...
```

> SF-099 `TenantMigrationRunner` (SF-178)'a bağımlı olduğu için aslında SF-178 sonrası gelir. Kritik yolu şöyle düzelt:

```markdown
**Faz 2:** SF-167 (DateTimeProvider bug) → **SF-178/179/180 (multi-tenancy/UNIQUE/hashCode fixes)** → SF-163/164 (error altyapısı) → **SF-099 → SF-101 → SF-102 (tenant signup verification, K-21)** → SF-071 → SF-072 → SF-073/074/075 → SF-168 (tokenInvalidBefore) → SF-096 → SF-097 → SF-130 → **SF-132 (ilk çalışan login ⭐)** → SF-150/151 (RBAC yönetimi) → SF-172/176 (log) → SF-330+ (UI).
```

## Temizlik

Değişiklikleri BACKLOG.md'ye uyguladıktan sonra şu yardımcı dosyaları sil:
- `BACKLOG2.md` (bu dosya)
- `_epic_2_0_c_block.md`
- `_test_create.txt`
- `_test_write.txt`
