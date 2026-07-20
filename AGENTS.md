# AGENTS.md

## Proje

**SystemForge** — modüler çok-kiracılı (multi-tenant) SaaS platformu. Java 21 + Spring Boot 4.1, PostgreSQL 16, Redis 7.4, Flyway. Hibrit model: built-in modüller (Tasks/Notes/Warehouse/Logistics — Odoo/ERPNext mantığı) + tenant custom app'leri (Notion/Airtable mantığı, JSONB EAV). **Schema-per-tenant** izolasyonu; **User-per-tenant** (global user yok); RBAC (User-Role + Group-Role + Role-Permission).

## Kurulum

Detaylı kurulum/çalıştırma `README.md`'de. Özet:

```bash
mvn clean install          # tüm modüller (testler H2'de, Docker gerektirmez)
docker compose up -d       # db + redis (dev infra)
# backend: SystemforgeApplication'ı IDE'den run/debug (dev profili)
# frontend: cd frontend && npm install && npm run dev
```

- `.env` yalnızca prod Docker Compose içindir; `dev` profilinde gerekmez. Asla commit edilmez (`.gitignore`'da).

## Komutlar

```bash
# Build
./mvnw clean install                       # tüm modüller + testler
./mvnw clean install -DskipTests           # hızlı build

# Backend çalıştır (root'tan DEĞİL — her zaman -pl backend)
./mvnw -pl backend spring-boot:run

# Test (dosya-bazlı tercih edilir)
./mvnw -pl backend test -Dtest=ClassName#methodName   # tek test
./mvnw -pl backend test                                # backend testleri
./mvnw test                                            # tüm suite (sadece açıkça istenirse)

# Frontend (frontend/ dizininde)
cd frontend && npm run dev      # :3000, /api -> :8080
cd frontend && npm run lint     # oxlint
cd frontend && npm run build    # tsc -b && vite build
```

> Testler `test` profilinde (H2 in-memory) çalışır → build Docker gerektirmez. Dev/prod PostgreSQL kullanır.

## Modüller

Her modülün kendi `AGENTS.md`'si var — modüle özgü kurallar orada.

- `common/` — paylaşılan çekirdek (`TenantContext`, paylaşılan exception'lar). **Spring/JPA YOK.** → `common/AGENTS.md`
- `persistence/` — JPA entity'ler + multi-tenancy altyapısı + Flyway migration. → `persistence/AGENTS.md`
- `backend/` — Spring Boot uygulaması (controller/service/security/config). Executable jar üretir. → `backend/AGENTS.md`
- `frontend/` — React 19 + TypeScript + Vite SPA. → `frontend/AGENTS.md`

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
- **Modüller arası döngüsel bağımlılık YASAK.** Bağımlılık grafiği: `common ← persistence ← backend`. `frontend` bağımsız.
- **Versiyonlar root `<properties>`'te** (`spring-boot.version`, `java.version`). Modül pom'larında version yazılmaz.
- **ID'ler her yerde UUID** (`GenerationType.UUID`). Tablo adları `t_` prefix'li.
- **Kod stili:** paket `com.ibrhalil.systemforge.*`, DTO'lar `record`, merkezi hata yönetimi `@RestControllerAdvice` (`ErrorResponse`), Lombok backend modülünde.

## Test

- `test` profili (H2 `MODE=PostgreSQL`) → `@SpringBootTest` `@ActiveProfiles("test")` ile. Build Docker gerektirmez.
- Dev/prod PostgreSQL. Dev profili `localhost:5432` default'ları gömülü.
- Yeni endpoint için en az bir test ekle. Tenant izolasyonu içeren değişiklikte ekstra dikkat.

## Sınırlar

**Asla:**
- `.env`, `application-prod.yaml` secret'larını, RSA key'lerini (`certs/*.pem`) commit etme / okuma.
- `ddl-auto`'yu `validate` yapma (multi-tenant + lazy tenant şeması yüzünden startup'ta çöker — her zaman `none`, şema Flyway'de).
- Cross-tenant sorgu yazma. Hassas veriyi (şifre, token, PII) log'a yazma.

**Önce sor:**
- Yeni Flyway migration eklerken (özellikle mevcut tenant şemalarını etkilerse — `TenantMigrationRunner` gerekir).
- Yeni bağımlılık eklerken (önce root pom'a uygun mu kontrol et).

**Her zaman:**
- Yeni endpoint'e test ekle.
- Servis katmanı yazma işlerinde `@Transactional` kullan (method-level; lookup'larda `readOnly=true`).

## Git

- Branch: `feat/SF-123-kisa-aciklama` (ticket ID `BACKLOG.md`'den). Merge sonrası branch silinir.
- Commit: Conventional Commits — `feat(tenant): add subdomain resolver`, `fix(auth): handle expired token`, `refactor: ...`, `test: ...`, `docs: ...`, `chore(deps): ...`. Subject <72 karakter, küçük harf, noktasız, imperative.
- Tüm PR'lar `develop`'a karşı. Squash merge. PR öncesi: `./mvnw test` + `npm run lint`.

## Dahası

- **Ticketlar / yol haritası:** `BACKLOG.md` (Faz 1.5-6, SF-001...SF-405).
- **Kurulum / konfigürasyon / API detayları:** `README.md`.
- **Mimari karar gerekçeleri:** ilgili modülün `AGENTS.md`'sinde.
