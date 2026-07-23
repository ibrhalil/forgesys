# forgesys

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61dafb.svg)](https://react.dev)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://www.postgresql.org)
[![Version](https://img.shields.io/badge/version-0.0.4-lightgrey.svg)]()

**Modüler çok-kiracılı (multi-tenant) SaaS platformu.** Şirketler (tenant) kayıt olur, kendi ekiplerini yönetir ve ihtiyaç duydukları **modülleri** açar (Tasks, Notes, Warehouse, Logistics) ya da kendi **custom app'lerini** (Notion-style database builder) yaratır. Hibrit model: built-in modüller (Odoo/ERPNext mantığı) + tenant custom app'leri (Notion/Airtable mantığı). Modül aktivasyonu **plan bazlıdır** (Free/Pro/Enterprise). Kullanıcılar rol-bazlı erişim kontrolü (RBAC) ile giriş yapar.

> **Vizyon:** Tek sabit ürün değil — esnek bir iş platformu. Bir şirket lojistiğini yönetir, bir diğeri deposunu, bir diğeri task/notlarını. Tenant ayrıca kendi ihtiyacına özel mini uygulama yaratabilir.

## Özellikler

**Mevcut (Faz 1 tamamlandı):**
- Multi-module Maven yapısı (`common` <- `persistence` <- `backend` + `frontend`)
- Schema-per-tenant multi-tenancy: subdomain çözümleme + Hibernate `SCHEMA` stratejisi
- Flyway per-schema migration (public auto-config + tenant programmatik)
- Tenant signup endpoint: `POST /api/v1/auth/company/register` — Company + schema + admin user oluşturur
- Entity hiyerarşisi: UUID, soft delete, optimistic locking, Spring Data auditing
- BCrypt password encoding, Bean Validation, merkezi hata yönetimi (`ApiErrorResponse` + `ErrorCode`)
- Docker: PostgreSQL + Redis + app (non-root), layered jars, actuator health

**Planlanan (kararlar kilitlendi — yol haritası [`docs/ROADMAP.md`](docs/ROADMAP.md)):**
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

# 3a. Backend — ForgeSysApplication'ı IntelliJ IDEA'dan run/debug et
#     (varsayılan "dev" profili -> localhost:5432 / localhost:6379)

# 3b. Frontend — Vite dev server http://localhost:3000
cd frontend
nvm use                              # Node 20.20.2 (.nvmrc'den) — ilk sefer zorunlu
npm install --include=optional       # lock dosyası üretmeden lokal kurulum
npm run dev                          # /api -> http://localhost:8080 proxy
```

> Proje `package-lock.json` kullanmaz (`.npmrc`: `package-lock=false`). Doğrudan bağımlılık sürümleri `package.json` içinde tam sürüm olarak sabittir; Maven ve Docker da `npm install --include=optional --no-package-lock` çalıştırır. Böylece native optional paketler kurulumu yapan işletim sistemine göre seçilir.

- Uygulama: http://localhost:8080 · Frontend: http://localhost:3000
- Veritabanı: `localhost:5432` (default: `forgesys` / `forgeadmin` / `forgepassword`) · Redis: `localhost:6379`

> **Build Docker gerektirmez.** Testler `test` profilinde (H2 in-memory) çalışır. Docker yalnızca dev infra'sı (db+redis) ve prod deploy için gerekli.

## Konfigürasyon

Konfigürasyon **profile-based** çalışır. Aktif profil `SPRING_PROFILES_ACTIVE` ile seçilir (varsayılan: `dev`). Profil detayları (DB, ddl-auto, flyway, H2 ayarları) tek source: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#konfigürasyon-profilleri).

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

### Production deployment (server — Debian/Ubuntu)

```bash
# 0. (One-time on the host) Install Docker Engine + Compose v2 plugin:
#    https://docs.docker.com/engine/install/debian/
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER   # re-login afterwards
docker --version && docker compose version

# 1. .env oluştur (gerçek secret'larla — .env.example şablonu)
cp .env.example .env
#   ...edit POSTGRES_PASSWORD, SPRING_DATASOURCE_PASSWORD, vb.

# 2. Bind-mount volume izinleri — postgres (UID 70) ve redis (UID 999)
#    host dizinlerine yazabilmeli. Linux native Docker bunu otomatik
#    yapmaz (Docker Desktop gibi user-namespace mapping yoktur).
sudo chown -R 70:70  infra/data/postgres && sudo chmod 700 infra/data/postgres
sudo chown -R 999:999 infra/data/redis

# 3. (Opsiyonel) test gate — Dockerfile kendi build'ini yapar, bu sadece testleri doğrular
mvn clean install

# 4. Tam stack'i build & başlat
docker compose -f docker-compose-prod.yml up -d --build
#   Kod değişikliği sonrası her yeniden deploy'da --build şart.
```

- App: http://localhost:8080 · Health: http://localhost:8080/actuator/health
- DB: `localhost:5432` (credential'lar `.env`'den) · Redis: `localhost:6379`

### Sadece Frontend (Backend yokken)

Frontend mock veriye düşer, backend çevrimdışı modda simülasyon çalıştırır:

```bash
cd frontend && npm install --include=optional && npm run dev
```

## Build Komutları

Hızlı başlangıç yukarıda. Bu bölüm **geliştirme/referans** için tüm yaygın komutları listeler.

### Maven (backend)

```bash
# Full build — tüm modüller (common, persistence, backend, frontend) + testler.
# frontend-maven-plugin npm install/build çalıştırır, çıktıyı backend jar'a gömer.
./mvnw clean install

# Sadece backend + upstream (frontend build yok — hızlı). CI bunu kullanır.
./mvnw clean install -pl backend -am

# Testleri atla (sadece derle + package)
./mvnw clean install -DskipTests

# Tek test sınıfı / tek metod
./mvnw -pl backend test -Dtest=AuditingTest
./mvnw -pl backend test -Dtest=AuditingTest#shouldAudit

# Backend'i terminalden çalıştır (IDE yerine)
./mvnw -pl backend spring-boot:run

# Debug çıktısı (bağımlılık/plugin sorunlarını araştırma — çok detaylı)
./mvnw clean install -X

# Error stack trace (build başarısızlığında detaylı hata)
./mvnw clean install -e

# Offline mode (bağımlılıklar önceden indirilmiş olmalı)
./mvnw -o clean install
```

### Frontend (npm)

```bash
cd frontend
npm install --include=optional   # lock dosyası yok (.npmrc: package-lock=false)
npm run lint                     # oxlint
npm run build                    # tsc -b && vite build -> dist/
npm run dev                      # http://localhost:3000 (/api -> :8080 proxy)
```

### Yaygın Maven flag'leri

| Flag | Açıklama |
|------|----------|
| `-pl <modül>` | Sadece belirtilen modülü build et (`backend`, `persistence`...) |
| `-am` | Also-make: belirtilen modülün upstream bağımlılıkları da build edilir |
| `-DskipTests` | Testleri çalıştırmadan derle/package et |
| `-Dtest=Class#method` | Belirli test(ler)i çalıştır (`*` wildcard destekler) |
| `-X` | Debug log (en detaylı) |
| `-e` | Error stack trace |
| `-o` | Offline mode (indirme yapma) |
| `--no-transfer-progress` | Bağımlılık indirme ilerlemesini gizle (CI-friendly) |
| `-T <n>` | Paralel build (`-T 4` = 4 thread) |

> Testler `test` profilinde H2 (MODE=PostgreSQL) ile çalışır -> build **Docker gerektirmez**. Dev/prod PostgreSQL kullanır. Detay: [`AGENTS.md`](AGENTS.md).

## API Endpoint'leri

Tüm endpoint'ler `/api/v1/*` prefix'i altında. Hata yanıtları tek tip `ApiErrorResponse` formatındadır (`code` + `traceId` + `fields[]`, `GlobalExceptionHandler`).

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

**Planlanan endpoint grupları** ([`docs/ROADMAP.md`](docs/ROADMAP.md)): Auth (`/auth/login` · `/refresh` · `/logout` · `/register` · `/me`), User CRUD (`/users`), RBAC (`/roles` · `/permissions` · `/groups`), Log (`/audit-logs` · `/login-history` · `/request-logs`), Modules (`/modules`), Custom Apps (`/apps`).

## Proje Yapısı

> Mimari diyagram, HTTP request yaşam döngüsü, şema-per-tenant modeli ve entity hiyerarşisi için bkz. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

```
forgesys/
├── pom.xml                  # Root POM — aggregator + version management (BOM import)
├── common/                  # Paylaşılan çekirdek — minimal bağımlılık (Spring/JPA YOK)
├── persistence/             # JPA entity'ler + çok-kiracılı altyapı + Flyway migration
├── backend/                 # Spring Boot uygulaması (executable jar üretir)
├── frontend/                # React + Vite SPA
├── infra/                   # Runtime altyapısı (config, data, logs, ssl, init-sql, templates)
├── docker-compose.yml       # Dev infra: PostgreSQL + Redis (app YOK, IDE'den çalışır)
├── docker-compose-prod.yml  # Prod stack: app + db + redis (.env'den okur)
├── Dockerfile               # Multi-stage: backend build -> runtime
├── AGENTS.md                # AI asistanları için kurallar (modül bazlı AGENTS.md'lerle)
└── docs/
    ├── ARCHITECTURE.md      # Mimari diyagram, request lifecycle, config profilleri
    ├── ROADMAP.md           # Faz/epik yol haritası (ticket numarasız)
    └── DECISIONS.md         # Karar kayıtları (K-XX/RISK-XX/DEBT-XX)
```

**`infra/` dizini** — kaynak kodu değil, runtime/operasyonel altyapıyı tutar (bkz. [`infra/README.md`](infra/README.md)):

| Alt dizin          | Amaç                                                            | Commit?                         |
|--------------------|----------------------------------------------------------------|---------------------------------|
| `config/`          | Prod için externalized Spring override'ları                     | Sadece `.gitkeep`               |
| `data/postgres/`   | PostgreSQL bind-mount volume (dev + prod)                       | Hayır (runtime veri)            |
| `data/redis/`      | Redis AOF bind-mount volume                                     | Hayır (runtime veri)            |
| `init-sql/`        | Docker `/docker-entrypoint-initdb.d/` script'leri (ilk kurulum) | Evet                            |
| `logs/`            | Spring Boot + container log bind-mount                          | Hayır (runtime veri)            |
| `ssl/`             | TLS sertifikaları (Nginx / app HTTPS)                           | **Hayır** — secret, asla commit |
| `templates/`       | Externalize runtime template'leri (mail HTML/CSS vb.)           | Evet                            |

**Modül bağımlılık grafiği (döngüsüz):** `common` <- `persistence` <- `backend` · `frontend` bağımsız. Sadece `backend` executable jar üretir; `common` ve `persistence` kütüphane jar'ıdır.

## Katkı Sağlama

### Dal Stratejisi (Branching)

- `main` — Production dalı. Her zaman deploy edilebilir.
- `develop` — Aktif geliştirme dalı.
- `feat/SF-NN-kisa-aciklama` — Yeni özellik (geliştirici kendi `SF-NN` numarasını verir, yol haritasına bağımlı değil).
- `fix/SF-NN-kisa-aciklama` — Hata düzeltme.
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

- **`mvnw: Permission denied`** -> `chmod +x mvnw`
- **Port 8080 / 3000 / 5432 kullanımda** -> `lsof -i :8080` ile bul, durdur.
- **Docker container DB'ye bağlanamıyor** -> önce `docker compose up db` ile DB'yi ayrı kaldır, `pg_isready` kontrol et.
- **PostgreSQL container "permission denied for data directory" (macOS)** -> bind-mount `infra/data/postgres/`'un sahibi `postgres` (UID 70) olmalı:
  ```bash
  sudo chown -R 70:70 infra/data/postgres && chmod 700 infra/data/postgres
  # Redis için (UID 999):
  sudo chown -R 999:999 infra/data/redis
  ```
- **DB verisini sıfırlamak** -> named volume yok artık; doğrudan host dizinini temizle:
  ```bash
  docker compose down
  rm -rf infra/data/postgres/* infra/data/redis/*
  ```
- **Backend ayağa kalkıyor ama frontend static servis etmiyor** -> `./mvnw clean install` (tüm modülleri yeniden build).
- **Frontend "Backend DOWN" gösteriyor** -> Backend çalışmıyor; başlat veya mock veriyle devam et (normal davranış).
- **Vite/Rolldown/Lightning CSS native binding bulunamıyor** -> Node 20.20.2'yi (`nvm use`) kullanıp `node_modules` dizinini temizleyerek `npm install --include=optional` çalıştır.

## Dahası

- **Mimari:** `docs/ARCHITECTURE.md` (bileşen diyagramı, request lifecycle, şema-per-tenant modeli, entity hiyerarşisi, config profilleri).
- **Yol haritası:** `docs/ROADMAP.md` (Faz/epik, ticket numarasız). **Karar kayıtları:** `docs/DECISIONS.md` (K-XX/RISK-XX/DEBT-XX).
- **AI asistanı kuralları:** `AGENTS.md` (kök) + her modülün kendi `AGENTS.md`'si (`common/`, `persistence/`, `backend/`, `frontend/`).

## License

[Apache License 2.0](LICENSE).
