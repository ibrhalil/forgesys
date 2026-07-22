# AGENTS.md

## Proje

**SystemForge** — modüler çok-kiracılı (multi-tenant) SaaS platformu. Java 21 + Spring Boot 4.1, PostgreSQL 16, Redis 7.4, Flyway. Hibrit model: built-in modüller (Tasks/Notes/Warehouse/Logistics — Odoo/ERPNext mantığı) + tenant custom app'leri (Notion/Airtable mantığı, JSONB EAV). **Schema-per-tenant** izolasyonu; **User-per-tenant** (global user yok); RBAC (User-Role + Group-Role + Role-Permission).

## Doküman Haritası

- [`README.md`](README.md) — kurulum, çalıştırma, **build komutları** (tek source), API, troubleshooting.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — mimari diyagram, request lifecycle, schema-per-tenant, entity hiyerarşisi, **config profilleri** (tek source).
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — faz/epik yol haritası (ticket numarasız, amaca odaklı).
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — karar kayıtları (K-XX mimari, RISK-XX risk, DEBT-XX teknik borç).
- Her modülün kendi `AGENTS.md`'si: [`common/`](common/AGENTS.md) · [`persistence/`](persistence/AGENTS.md) · [`backend/`](backend/AGENTS.md) · [`frontend/`](frontend/AGENTS.md).

## Kurulum (özet)

Detay ve tüm komutlar `README.md`'de. Özet:

```bash
mvn clean install          # tüm modüller (testler H2'de, Docker gerektirmez)
docker compose up -d       # db + redis (dev infra)
# backend: SystemforgeApplication'ı IDE'den run/debug (dev profili)
# frontend: cd frontend && npm install --include=optional && npm run dev
```

- `.env` yalnızca prod Docker Compose içindir; `dev` profilinde gerekmez. Asla commit edilmez (`.gitignore`'da).

## Modüller

Her modülün kendi `AGENTS.md`'si var — modüle özgü kurallar orada.

- `common/` — paylaşılan çekirdek (`TenantContext`, paylaşılan exception'lar). **Spring/JPA YOK.**
- `persistence/` — JPA entity'ler + multi-tenancy altyapısı + Flyway migration.
- `backend/` — Spring Boot uygulaması (controller/service/security/config). Executable jar üretir.
- `frontend/` — React 19 + TypeScript + Vite SPA.

## Operasyonel Altyapı (`infra/`)

Kaynak kodu değil, runtime/operasyonel dosyalar. Detaylar `infra/README.md`'de.

- `infra/config/` — prod externalized override. İçine `application-prod.yaml` bırakırsan jar'dakini geçersiz kılar (`SPRING_CONFIG_ADDITIONAL_LOCATION`). **Secret varsa commit etme.**
- `infra/data/{postgres,redis}/` — bind-mount volume. **Commit edilmez.** macOS'te izin sorunu için postgres UID 70, redis UID 999.
- `infra/init-sql/` — Docker postgres `/docker-entrypoint-initdb.d/` script'leri. **SADECE ilk DB yaratımında** çalışır (extension, rol). **Flyway migration'larından tamamen ayrı** — karıştırma.
- `infra/logs/` — Spring Boot file appender + container log bind-mount. **Commit edilmez.**
- `infra/ssl/` — TLS sertifikaları / private key'ler. **ASLA commit etme** (kök "Sınırlar" kuralıyla çakışır).
- `infra/templates/` — externalize runtime template'leri (mail HTML/CSS vb.).

**init-sql vs Flyway (kritik ayrım):** Flyway her startup'ta `flyway_schema_history`'den çalışır (versioned). `init-sql/` ise postgres image'ı tarafından yalnızca **data directory boşsa** (ilk kurulum) çalışır. Aynı dosyayı iki yere koyma — Flyway checksum/history tutarsızlığı çöker.

## Kritik Kurallar (tüm modüller)

- **Tenant izolasyonu ZORUNLU.** Hiçbir sorgu tenant filtresiz olmamalı. Tenant verisi sızdıran en kritik bug sınıfıdır. Tenant bağlamı `TenantFilter` tarafından kurulur (`common.TenantContext` ThreadLocal); controller'da tenant doğrulama YAPMA.
- **Kök pom sadece lightweight parent + aggregator'dır** — modüllere bağımlılık dayatmaz (`<dependencies>` yok), sadece version management sağlar. Hiçbir modül `spring-boot-starter-parent`'ı parent yapmaz.
- **Modüller arası döngüsel bağımlılık YASAK.** Bağımlılık grafiği: `common` <- `persistence` <- `backend`. `frontend` bağımsız.
- **Versiyonlar root `<properties>`'te** (`spring-boot.version`, `java.version`). Modül pom'larında version yazılmaz.
- **ID'ler her yerde UUID** (`GenerationType.UUID`). Tablo adları `t_` prefix'li.
- **Kod stili:** paket `com.ibrhalil.systemforge.*`, DTO'lar `record`, merkezi hata yönetimi `@RestControllerAdvice` (`ErrorResponse`), Lombok backend modülünde.

## Test

- Config profilleri (dev/prod/test, H2, ddl-auto, flyway.enabled) tek source: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#konfigurasyon-profilleri).
- Yeni endpoint için en az bir test ekle. Tenant izolasyonu içeren değişiklikte ekstra dikkat.

## Sınırlar

**Asla:**
- **Habersiz git işlemi yapma.** `git commit`, `git push`, `git amend`, `git merge`, `git rebase`, `git reset --hard`, branch oluşturma/silme, `gh pr create` vb. — bunların HEPSİ yalnızca kullanıcı **açıkça** istediğinde yapılır. "İşim bitti, commit atayım" gibi inisiyatif ALMA. Kullanıcı commit/push demeden staging alanında kal. `git add`/`git status`/`git diff`/`git log` (salt-okunur) serbesttir.
- `.env`, `application-prod.yaml` secret'larını, RSA key'lerini (`certs/*.pem`) commit etme / okuma.
- `ddl-auto`'yu `validate` yapma (multi-tenant + lazy tenant şeması yüzünden startup'ta çöker — her zaman `none`, şema Flyway'de). Test profili istisnası: `create-drop`.
- Cross-tenant sorgu yazma. Hassas veriyi (şifre, token, PII) log'a yazma.

**Önce sor:**
- Yeni Flyway migration eklerken (özellikle mevcut tenant şemalarını etkilerse — `TenantMigrationRunner` gerekir, bkz. [RISK-16](docs/DECISIONS.md#risk-16--yeni-tenant-migration-mevcut-tenantlarda-calismaz)).
- Yeni bağımlılık eklerken (önce root pom'a uygun mu kontrol et).

**Her zaman:**
- Yeni endpoint'e test ekle.
- Servis katmanı yazma işlerinde `@Transactional` kullan (method-level; lookup'larda `readOnly=true`). **İstisna:** `provisionTenant` şu an transaction'suz ([DEBT-10](docs/DECISIONS.md#debt-10--provisiontenant-transactionsuz)), K-21 ile düzeltilecek.

## Git

> **Aşağıdaki kurallar YALNIZCA kullanıcı commit/push/PR'ı açıkça istediğinde uygulanır.** Agent habersiz commit, push, amend, merge, branch oluşturma/silme veya PR yapamaz — bkz. yukarıdaki "Sınırlar / Asla". Değişiklikleri staging'de bırak, kullanıcı `git add`/`commit`/`push`/`gh pr create` demeden işlem yapma.

- **Branch:** `feat/SF-NN-kisa-aciklama` — geliştirici kendi `SF-NN` numarasını verir, yol haritasına bağımlı değil. Merge sonrası branch silinir.
- **Commit:** Conventional Commits — `feat(tenant): add subdomain resolver`, `fix(auth): handle expired token`, `refactor: ...`, `test: ...`, `docs: ...`, `chore(deps): ...`. Subject <72 karakter, küçük harf, noktasız, imperative.
- Tüm PR'lar `develop`'a karşı. Squash merge. PR öncesi: `./mvnw test` + `npm run lint`.
