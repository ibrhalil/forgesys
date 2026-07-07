# systemforge

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61dafb.svg)](https://react.dev)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://www.postgresql.org)
[![Version](https://img.shields.io/badge/version-0.0.3-lightgrey.svg)](#)

**Modüler çok-kiracılı (multi-tenant) SaaS platformu.** Şirketler (tenant) kayıt olur, kendi ekiplerini yönetir ve ihtiyaç duydukları **modülleri** açar (Tasks, Notes, Warehouse, Logistics) ya da kendi **custom app'lerini** (Notion-style database builder) yaratır. Hibrit model: built-in modüller (Odoo/ERPNext mantığı) + tenant custom app'leri (Notion/Airtable mantığı). Modül aktivasyonu **plan bazlıdır** (Free/Pro/Enterprise). Kullanıcılar rol-bazlı erişim kontrolü (RBAC) ile giriş yapar.

> **Vizyon:** Tek sabit ürün değil — esnek bir iş platformu. Bir şirket lojistiğini yönetir, bir diğeri deposunu, bir diğeri task/notlarını. Tenant ayrıca kendi ihtiyacına özel mini uygulama yaratabilir.

## Özellikler

**Mevcut (Faz 1 tamamlandı):**
- Multi-module Maven yapısı (`common` ← `persistence` ← `backend` + `frontend`)
- Schema-per-tenant multi-tenancy: subdomain çözümleme + Hibernate `SCHEMA` stratejisi
- Flyway per-schema migration (public auto-config + tenant programmatik)
- Tenant signup endpoint: `POST /api/v1/auth/company/register` — Company + schema + admin user oluşturur
- Entity hiyerarşisi: UUID, soft delete, optimistic locking, Spring Data auditing
- BCrypt password encoding, Bean Validation, merkezi hata yönetimi (`ErrorResponse`)
- Docker: PostgreSQL + Redis + app (non-root), layered jars, actuator health

**Planlanan (kararlar kilitlendi — yol haritası `BACKLOG.md`):**
- Spring Security + JWT (login/refresh/logout, httpOnly cookie) + RBAC yönetimi
- 3 katmanlı log (audit + giriş geçmişi + request/trace)
- **Built-in modüller:** Tasks, Notes, Warehouse, Logistics (plan bazlı aktivasyon)
- **Custom App Builder** (Notion-style, JSONB EAV)
- Billing (Stripe/iyzico), Nginx gateway, CI/CD

## Teknoloji Stack'i

**Backend:** Java 21, Spring Boot 4.1, Spring Data JPA (Hibernate), PostgreSQL 16 (dev + prod), Redis 7.4 (cache + token blacklist), Flyway (per-schema migration), spring-security-crypto (BCrypt), JUnit 5, Lombok.

**Frontend:** React 19, TypeScript 6, Vite 8, oxlint (lint).

**DevOps:** Docker multi-stage (layered jars), Docker Compose v2, runtime `eclipse-temurin:21-jre-alpine` (non-root, JVM container awareness).

## Kurulum

### Ön Koşullar

| Araç | Minimum Sürüm | Zorunluluk |
|------|---------------|------------|
| JDK | 21 | Zorunlu |
| Maven | 3.9+ (veya bundled `mvnw`) | Zorunlu |
| Node.js | 20.20.2 (`.nvmrc` ile kilit, `nvm use`/`fnm use` gerekli) | Frontend için |
| npm | 10.x (Node 20 ile gelir) | Frontend için |
| Docker | 24+ | Tam stack için |
| Docker Compose | v2+ | Tam stack için |

### Hızlı Başlangıç (lokal dev — önerilen)

Backend IDE'de (debug), frontend Vite HMR'de (:3000); sadece bağımlılıklar Docker'da. Vite `/api` proxy backend'e.

```bash
# 1. Tüm modülleri build et (testler H2 "test" profilinde çalışır — Docker gerektirmez)
mvn clean install            # veya: mvn clean install -DskipTests

# 2. PostgreSQL + Redis'i başlat (yeni checkout'ta .env gerekmez)
docker compose up -d

# 3a. Backend — SystemforgeApplication'ı IntelliJ IDEA'dan run/debug et
#     (varsayılan "dev" profili -> localhost:5432 / localhost:6379)

# 3b. Frontend — Vite dev server http://localhost:3000
cd frontend
nvm use                              # Node 20.18.0 (.nvmrc'den) — ilk sefer zorunlu
npm install                          # package-lock.json yeniden üretir (engines field kontrol eder)
npm run dev                          # /api -> http://localhost:8080 proxy
```

- Uygulama: http://localhost:8080 · Frontend: http://localhost:3000
- Veritabanı: `localhost:5432` (default: `systemforge` / `forgeadmin` / `forgepassword`) · Redis: `localhost:6379`

> **Build Docker gerektirmez.** Testler `test` profilinde (H2 in-memory) çalışır. Docker yalnızca dev infra'sı (db+redis) ve prod deploy için gerekli.

## Konfigürasyon

Konfigürasyon **profile-based** çalışır. Aktif profil `SPRING_PROFILES_ACTIVE` ile seçilir (varsayılan: `dev`).

| Profil | DB | Kullanım | `.env` gerekir mi? |
|--------|----|---------|----|
| `dev` (varsayılan) | PostgreSQL (`localhost:5432` default'ları gömülü) | IDE debug | Hayır |
| `prod` | PostgreSQL (credential'lar `.env`'den) | `docker-compose-prod.yml` | **Evet** |
| `test` | H2 in-memory (`MODE=PostgreSQL`) | `@SpringBootTest` `@ActiveProfiles("test")` | Hayır |

**Önemli ortam değişkenleri (prod):**

| Değişken | Açıklama |
|----------|----------|
| `SPRING_PROFILES_ACTIVE` | Aktif Spring profili (`prod`) |
| `SPRING_DATASOURCE_URL` | DB bağlantı URL'i |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | DB credential'ları |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | Redis |
| `BASE_DOMAIN` | Subdomain çözümleme için base domain |

> `.env` yalnızca **prod** Docker Compose içindir; `dev` profilinde gerekmez. `.env` `.gitignore`'dadır, asla commit edilmez. Şablon: `.env.example`.

## Çalıştırma

### Production deployment (server)

```bash
# 1. .env oluştur (gerçek secret'larla — .env.example şablonu)
cp .env.example .env
#   ...edit POSTGRES_PASSWORD, SPRING_DATASOURCE_PASSWORD, vb.

# 2. (Opsiyonel) test gate — Dockerfile kendi build'ini yapar, bu sadece testleri doğrular
mvn clean install

# 3. Tam stack'i build & başlat
docker compose -f docker-compose-prod.yml up -d --build
#   Kod değişikliği sonrası her yeniden deploy'da --build şart.
```

- App: http://localhost:8080 · Health: http://localhost:8080/actuator/health
- DB: `localhost:5432` (credential'lar `.env`'den) · Redis: `localhost:6379`

### Sadece Frontend (Backend yokken)

Frontend mock veriye düşer, backend çevrimdışı modda simülasyon çalıştırır:

```bash
cd frontend && npm install && npm run dev
```

## API Endpoint'leri

Tüm endpoint'ler `/api/v1/*` prefix'i altında. Hata yanıtları tek tip `ErrorResponse` formatında (`GlobalExceptionHandler`).

| Method | Path | Açıklama | Auth |
|--------|------|----------|------|
| `POST` | `/api/v1/auth/company/register` | Yeni tenant signup + admin user oluşturma | Public |

**Örnek istek:**

```bash
curl -X POST http://localhost:8080/api/v1/auth/company/register \
  -H "Content-Type: application/json" \
  -d '{
    "companyName": "Acme Corp",
    "subdomain": "acme",
    "emailDomain": "acme.com",
    "adminEmail": "admin@acme.com",
    "adminPassword": "secure-password-123",
    "adminFirstName": "John",
    "adminLastName": "Doe"
  }'
```

**Başarılı yanıt (201):**

```json
{ "id": "uuid...", "name": "Acme Corp", "subdomain": "acme", "schemaName": "tenant_acme" }
```

> Bu endpoint `TenantFilter`'dan muaf tutulur (`shouldNotFilter`) — zaten tenant'ı oluşturuyor.

**Planlanan endpoint grupları** (`BACKLOG.md`): Auth (`/auth/login` · `/refresh` · `/logout` · `/register` · `/me`), User CRUD (`/users`), RBAC (`/roles` · `/permissions` · `/groups`), Log (`/audit-logs` · `/login-history` · `/request-logs`), Modules (`/modules`), Custom Apps (`/apps`).

## Proje Yapısı

> Mimari diyagram, HTTP request yaşam döngüsü, şema-per-tenant modeli ve entity hiyerarşisi için bkz. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

```
systemforge/
├── pom.xml                  # Root POM — aggregator + version management (BOM import)
├── common/                  # Paylaşılan çekirdek — minimal bağımlılık (Spring/JPA YOK)
├── persistence/             # JPA entity'ler + çok-kiracılı altyapı + Flyway migration
├── backend/                 # Spring Boot uygulaması (executable jar üretir)
├── frontend/                # React + Vite SPA
├── docker-compose.yml       # Dev infra: PostgreSQL + Redis (app YOK, IDE'den çalışır)
├── docker-compose-prod.yml  # Prod stack: app + db + redis (.env'den okur)
├── Dockerfile               # Multi-stage: backend build -> runtime
├── AGENTS.md                # AI asistanları için kurallar (modül bazlı AGENTS.md'lerle)
└── BACKLOG.md               # Ticket / yol haritası (SF-001...SF-405)
```

**Modül bağımlılık grafiği (döngüsüz):** `common ← persistence ← backend` · `frontend` bağımsız. Sadece `backend` executable jar üretir; `common` ve `persistence` kütüphane jar'ıdır.

## Katkı Sağlama

### Dal Stratejisi (Branching)

- `main` — Production dalı. Her zaman deploy edilebilir.
- `develop` — Aktif geliştirme dalı.
- `feat/SF-123-kisa-aciklama` — Yeni özellik (ticket ID `BACKLOG.md`'den).
- `fix/SF-123-kisa-aciklama` — Hata düzeltme.
- Tüm PR'lar `develop`'a karşı. Squash merge. Merge sonrası branch silinir.

### Commit Convention

Conventional Commits kullanılır: `<type>(<scope>): <subject>`

- `feat` — Yeni özellik (`feat(tenant): add subdomain resolver`)
- `fix` — Hata düzeltme (`fix(auth): handle expired token`)
- `refactor` — Yeniden yapılandırma (`refactor(tenant): split filter into resolver`)
- `test` · `docs` · `chore(deps)` · `ci`

Kurallar: Subject <72 karakter, küçük harfle başlasın, nokta ile bitmesin, imperative mood ("add" değil "added").

### Code Review

- Build + test + lint geçmek zorunlu: PR öncesi `./mvnw test` + `npm run lint`.
- Tenant izolasyonu içeren değişikliklerde ekstra dikkat (data leak kontrolü) — tenant verisi sızdıran en kritik bug sınıfıdır.
- Yeni endpoint'ler için en az bir test eklenmeli.

### Troubleshooting

- **`mvnw: Permission denied`** → `chmod +x mvnw`
- **Port 8080 / 3000 / 5432 kullanımda** → `lsof -i :8080` ile bul, durdur.
- **Docker container DB'ye bağlanamıyor** → önce `docker compose up db` ile DB'yi ayrı kaldır, `pg_isready` kontrol et. Eski volume sorununda `docker compose down -v` (veri gider).
- **Backend ayağa kalkıyor ama frontend static servis etmiyor** → `./mvnw clean install` (tüm modülleri yeniden build).
- **Frontend "Offline Mode" gösteriyor** → Backend çalışmıyor; başlat veya mock veriyle devam et (normal davranış).
- **`npm ci` Docker build'de fail** → `package-lock.json` commit edildi mi kontrol et.

## Dahası

- **Mimari:** `docs/ARCHITECTURE.md` (bileşen diyagramı, request lifecycle, şema-per-tenant modeli, entity hiyerarşisi).
- **AI asistanı kuralları:** `AGENTS.md` (kök) + her modülün kendi `AGENTS.md`'si (`common/`, `persistence/`, `backend/`, `frontend/`).
- **Ticketlar / yol haritası:** `BACKLOG.md` (Faz 1.5-6, SF-001...SF-405).

## License

[Apache License 2.0](LICENSE).
