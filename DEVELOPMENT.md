# Geliştirme Rehberi (Developer Guide)

Bu rehber, SystemForge projesine katkı sağlayacak geliştiriciler için kurulum, mimari, geliştirme akışı ve deployment bilgilerini içerir.

> **Not:** Proje aktif geliştirme aşamasındadır. Aşağıdaki bölümlerde "Mevcut" ve "Hedef" ayrımı yapılmıştır. Detaylı ilerleyiş için `AGENT.md` dosyasına bakınız.

---

## İçindekiler

- [Ön Koşullar](#ön-koşullar)
- [Hızlı Başlangıç](#hızlı-başlangıç)
- [Proje Yapısı](#proje-yapısı)
- [Mimari](#mimari)
- [Konfigürasyon](#konfigürasyon)
- [Geliştirme Akışı](#geliştirme-akışı)
- [Build & Test](#build--test)
- [Frontend Geliştirme](#frontend-geliştirme)
- [Backend Geliştirme](#backend-geliştirme)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)

---

## Ön Koşullar

| Araç | Minimum Sürüm | Zorunluluk |
|------|---------------|------------|
| JDK | 21 | Zorunlu |
| Maven | 3.9+ (veya bundled `mvnw`) | Zorunlu |
| Node.js | 20+ (Docker build için) | Frontend için |
| Docker | 24+ | Tam stack için |
| Docker Compose | v2+ | Tam stack için |
| PostgreSQL | 16 (Docker ile geliyorsa) | Prod/Integration için |
| Redis | 7.4 (Docker ile gelecek) | Cache/Token blacklist için |

### Teknoloji Yığını (Özet)

**Backend:** Java 21, Spring Boot, Spring Data JPA (Hibernate), PostgreSQL 16, **Redis 7.4** (cache + token blacklist), **Flyway** (per-schema migration), Spring Security + **JWT (jjwt)**, JUnit 5 + Mockito + **Testcontainers**, Lombok, Checkstyle/PMD.

**Frontend:** React 19, TypeScript, Vite, **TanStack Query v5** (server state), **Zustand v5** (client state), **Tailwind CSS** (styling), Vitest + React Testing Library + Playwright (E2E), ESLint 9 Flat Config / Oxlint.

**DevOps:** Docker multi-stage, Docker Compose v2, GitHub Actions (CI/CD), runtime `eclipse-temurin:21-jre-alpine`.

IDE önerileri: IntelliJ IDEA Ultimate (Spring + DB entegrasyonu) veya VS Code.

---

## Hızlı Başlangıç

Üç farklı kurulum yolu vardır. İhtiyacına göre birini seç.

### Seçenek A — Tam Stack (Docker ile, önerilen)
Backend + Frontend + PostgreSQL tek komutla ayağa kalkar. Frontend build alınıp backend jar'ına gömülür.

```bash
docker-compose up --build
```

- Uygulama: http://localhost:8080
- Veritabanı: `localhost:5432` (DB: `systemforge`, user: `forgeadmin`, pass: `forgepassword`)

### Seçenek B — Lokal Geliştirme (Backend + Frontend ayrı)
İki terminal aç. Backend H2 (in-memory) ile çalışır, DB kurulumu gerekmez.

**Terminal 1 — Backend:**
```bash
./mvnw -pl backend spring-boot:run
# Backend: http://localhost:8080
```

**Terminal 2 — Frontend (sıcak reload için):**
```bash
cd frontend
npm install
npm run dev
# Frontend: http://localhost:3000  (/api -> :8080 proxy)
```

### Seçenek C — Sadece Frontend (Backend yokken)
Frontend mock veriye düşer, backend çevrimdışı modda simülasyon çalıştırır.

```bash
cd frontend
npm install
npm run dev
```

---

## Proje Yapısı

### Root Pom Prensibi

**Root pom lightweight parent + aggregator'dır.** Modüllere bağımlılık DAYATMAZ (`<dependencies>` yok) ama merkezi version management sağlar. Bu enterprise standardı: tek yerden version yönetimi + opt-in bağımlılık modeli.

- **Root pom içeriği:** `<properties>` (java.version, spring-boot.version, encoding), `<dependencyManagement>` (Spring Boot BOM import + internal modüller), `<pluginManagement>` (compiler, surefire, spring-boot-maven-plugin version'ları).
- **Spring Boot BOM import:** Root `<dependencyManagement>`'da `spring-boot-dependencies` BOM import edilir. Bu, Spring Boot uyumlu version set'lerini merkezi sağlar. Modüllerde `<version>` YAZILMAZ — BOM yönetir.
- **BOM import ≠ Spring dayatması:** `dependencyManagement` bağımlılık EKLEMEZ, sadece version yönetir. `common` modülü BOM import'undan etkilenmez — sadece kendi deklare ettiği `slf4j-api` ve `junit-jupiter`'ı alır.
- **Tüm modüller root'u parent yapar:** `spring-boot-starter-parent` hiçbir modülün parent'ı DEĞİL. Spring Boot'un parent davranışı (auto-config, plugin defaults) BOM + explicit plugin config ile sağlanır.
- **Version bump tek yerden:** `spring-boot.version` veya `java.version` değiştiğinde sadece root pom düzenlenir.
- **Backend explicit config:** `spring-boot-maven-plugin`'in `repackage` goal'i backend'de explicit olarak tanımlı (parent auto-config olmadığından).
- **Yayınlama ihtiyacı doğunca:** `${revision}` + `flatten-maven-plugin` pattern'i eklenir (Nexus/Artifactory publish, CI release pipeline).

### Dizin Yapısı

```
systemforge/
├── pom.xml                  # Root POM — sadece AGGREGATOR (revision + modules + encoding)
├── mvnw / mvnw.cmd          # Maven wrapper
├── docker-compose.yml       # PostgreSQL + app servisleri
├── Dockerfile               # Multi-stage: frontend build -> backend build -> runtime
├── AGENT.md                 # Proje takip dokümanı (opencode yönetimi)
├── DEVELOPMENT.md           # Bu dosya
│
├── common/                  # (Faz 1) Paylaşılan çekirdek — minimal bağımlılık
│   ├── pom.xml              #   Spring/JPA yok, sadece util/dto/context
│   └── src/main/java/com/ibrhalil/systemforge/common/
│       ├── tenant/          #   TenantContext (ThreadLocal<String>)
│       ├── exception/       #   Paylaşılan exception'lar
│       ├── dto/             #   ErrorResponse gibi paylaşılan record'lar
│       └── util/
│
├── persistence/             # (Faz 1) JPA + çok-kiracılı altyapı
│   ├── pom.xml              #   spring-data-jpa + hibernate + common
│   └── src/main/java/com/ibrhalil/systemforge/persistence/
│       ├── tenant/          #   MultiTenantConnectionProvider, CurrentTenantIdentifierResolver
│       ├── config/          #   Hibernate çok-kiracılı konfigürasyon
│       ├── domain/
│       │   ├── master/      #   Tenant, User, TenantMembership (public şema)
│       │   └── tenant/      #   Project, Task, Team (her tenant'ın şeması)
│       └── repository/
│
├── backend/                 # Spring Boot uygulaması (executable jar üretir)
│   ├── pom.xml              #   spring-boot-starter-* + common + persistence + lombok
│   └── src/
│       ├── main/
│       │   ├── java/com/ibrhalil/systemforge/
│       │   │   ├── SystemforgeApplication.java
│       │   │   ├── tenant/         # TenantFilter (subdomain), TenantResolver
│       │   │   ├── controller/     # REST endpoint'leri (/api/v1/*)
│       │   │   ├── service/        # İş mantığı
│       │   │   ├── security/       # Spring Security, JWT/filter
│       │   │   └── config/
│       │   └── resources/
│       │       ├── application.yaml
│       │       └── static/         # Frontend build çıktısı (otomatik kopyalanır)
│       └── test/
│
└── frontend/                # React + Vite modülü
    ├── pom.xml              # frontend-maven-plugin (Node/npm install + build)
    ├── package.json
    ├── vite.config.ts       # Dev server :3000, /api -> :8080 proxy
    └── src/
        ├── App.tsx          # Ana dashboard
        ├── main.tsx         # Entry point
        └── index.css
```

### Modül Bağımlılık Grafiği

```
common      ← (bağımlılık yok — lightweight çekirdek)
   ↑
persistence ← common (JPA + Hibernate ekler)
   ↑
backend     ← common + persistence (Spring Boot starters ekler)
frontend    ← (bağımlılık yok, bağımsız npm build)
```

**Kurallar:**
- Sadece `backend` executable jar üretir. `common` ve `persistence` kütüphane jar'ıdır.
- Modüller arası döngüsel bağımlılık YASAK.
- `common` modülüne Spring/JPA bağımlılığı EKLENMEZ — bu kuralın ihlali modül amacını bozar.
- `persistence` modülüne web/security bağımlılığı EKLENMEZ.
- Yeni modül eklerken root pom `<modules>` listesine ekle, parent davranışı ekleme.

---

## Mimari

### Build Pipeline

```
[frontend/dist]  --(maven-resources-plugin)-->  [backend/resources/static]
                            |
                            v
              [mvn package -pl backend]
                            |
                            v
              systemforge-backend.jar  (self-contained, static frontend gömülü)
                            |
                            v
              [eclipse-temurin:21-jre-alpine]  (Docker runtime)
```

- **Multi-stage Dockerfile:**
  1. `node:20-alpine` → frontend build
  2. `maven:3.9.6-eclipse-temurin-21-alpine` → backend build (frontend çıktısı static'e kopyalanır)
  3. `eclipse-temurin:21-jre-alpine` → minimal runtime (root olmayan kullanıcı)

### Multi-Tenancy (Stratejik Karar)

> **Hedef:** Schema-per-tenant + subdomain çözümleme.
> **Mevcut:** Header-tabanlı (`X-Tenant-ID`) + ThreadLocal, shared-schema varsayımı.
> Mevcut yapı, hedef mimariye ulaşılınca yeniden yazılacaktır. Detaylar `AGENT.md` içinde.

**Mevcut akış (geçici):**
```
HTTP İsteği
   │
   ▼
[TenantFilter]  ──> X-Tenant-ID header'ı okur
   │
   ▼
[TenantContext]  (ThreadLocal<String>)
   │
   ▼
[Controller]  ──> TenantContext.getCurrentTenant()
```

**Hedef akış (Faz 1 sonrası):**
```
acme.systemforge.com
   │
   ▼
[TenantFilter]  ──> subdomain parse (acme)
   │
   ▼
[Master Şema Lookup]  ──> tenant_acme şeması çözümlenir
   │
   ▼
[MultiTenantConnectionProvider]  ──> Hibernate bağlantısı o şemaya set edilir
   │
   ▼
[Repository]  ──> sadece o şemadan veri okur
```

### Paket Kuralları (Backend)

Tüm backend kodu `com.ibrhalil.systemforge.*` altındadır. Modüllere göre dağılım:

| Modül | Kök Paket | İçerik | Spring/JPA var mı? |
|-------|-----------|--------|---------------------|
| `common` | `com.ibrhalil.systemforge.common` | TenantContext, paylaşılan exception/DTO/util | **HAYIR** |
| `persistence` | `com.ibrhalil.systemforge.persistence` | Entity, repository, multi-tenancy connection provider | Sadece JPA |
| `backend` | `com.ibrhalil.systemforge` | Controller, service, security, main, config | Tam Spring Boot |

Yeni katmanlar için önerilen paketler (`backend` modülü içinde):

```
com.ibrhalil.systemforge
├── tenant/        # TenantFilter (subdomain), TenantResolver
├── controller/    # REST controller'ları (/api/v1/*)
├── service/       # İş mantığı
├── security/      # Spring Security config, JWT/filter, RBAC
├── dto/           # Request/Response DTO'ları (backend'e özel, record tercih edilir)
└── config/        # Uygulama konfigürasyonu
```

**Paylaşılan** tipler (birden fazla modülün kullandığı) `common`'a konur; sadece `backend`'in kullandığı tipler `backend`'de kalır.

---

## Konfigürasyon

### Ortam Değişkenleri

`backend/src/main/resources/application.yaml` env variable üzerinden yapılandırılır. Tanımsız değerler H2 varsayılanına düşer.

| Değişken | Varsayılan (dev) | Açıklama |
|----------|------------------|----------|
| `SPRING_DATASOURCE_URL` | `jdbc:h2:mem:systemforge;DB_CLOSE_DELAY=-1` | DB bağlantı URL'i |
| `SPRING_DATASOURCE_USERNAME` | `sa` | DB kullanıcısı |
| `SPRING_DATASOURCE_PASSWORD` | (boş) | DB şifresi |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | `org.h2.Driver` | JDBC driver |
| `SPRING_JPA_DATABASE_PLATFORM` | `org.hibernate.dialect.H2Dialect` | Hibernate dialect |
| `SPRING_PROFILES_ACTIVE` | (yok) | Dockerfile'da `prod` set edilir |

### Profiller

- **Varsayılan (dev):** H2 in-memory, `ddl-auto=update`. Hızlı prototip için.
- **Prod (Docker):** PostgreSQL, `SPRING_PROFILES_ACTIVE=prod`. `application-prod.yaml` henüz YOK — Faz 5'te eklenecek.

> **Önemli:** `ddl-auto=update` production için güvenli değildir. Faz 1 sonrası Flyway per-schema migration'a geçilecektir.

---

## Geliştirme Akışı

### Dal Stratejisi (Branching)

- `main` — Production dalı. Her zaman deploy edilebilir olmalı.
- `develop` — Aktif geliştirme dalı.
- `feature/<kısa-açıklama>` — Yeni özellikler (`feature/tenant-signup`).
- `fix/<kısa-açıklama>` — Hata düzeltmeleri (`fix/tenant-filter-leak`).
- `refactor/<kısa-açıklama>` — Yeniden yapılandırma.

Örnek akış:
```bash
git checkout develop
git pull
git checkout -b feature/tenant-entity
# ... geliştirme ...
git push -u origin feature/tenant-entity
# Pull request: feature/tenant-entity -> develop
```

### Çalıştırma Komutları

```bash
# Backend'i çalıştır (H2 ile, standalone)
./mvnw -pl backend spring-boot:run

# Backend'i çalıştır (önce install gerekir, ilk seferde)
./mvnw clean install -DskipTests && ./mvnw -pl backend spring-boot:run
```

> **Önemli:** `spring-boot:run` root'tan çalışmaz (root aggregator'dır, Spring Boot eklentisi yok). Her zaman `-pl backend` ile çalıştır.

### Commit Convention

Conventional Commits kullanılır:

```
<type>(<scope>): <subject>

[opsiyonel body]
```

**Tipler:**
- `feat` — Yeni özellik (`feat(tenant): add subdomain resolver`)
- `fix` — Hata düzeltme (`fix(auth): handle expired token`)
- `refactor` — Refactor (`refactor(tenant): split filter into resolver`)
- `docs` — Doküman (`docs: update DEVELOPMENT.md`)
- `test` — Test (`test(tenant): add connection provider tests`)
- `chore` — Yapısal (`chore(deps): upgrade spring boot`)
- `ci` — CI/CD (`ci: add github actions workflow`)

**Kurallar:**
- Subject satırı 72 karakteri geçmesin.
- Subject küçük harfle başlasın, nokta ile bitmesin.
- Imperative mood ("add" değil "added"): "add tenant resolver".
- Pull request'lerde her commit anlamlı ve tek sorumluluklu olsun (squash merge önerilir).

### Code Review Kuralları

- Tüm PR'lar `develop`'a karşı açılır.
- Build + test + lint geçmek zorunlu.
- Tenant izolasyonu içeren değişikliklerde ekstra dikkat (data leak kontrolü).
- Yeni endpoint'ler için en az bir entegrasyon testi eklenmeli.

---

## Build & Test

### Maven (kök dizinden)

```bash
# Tüm modülleri build et (frontend dahil) — backend jar'ı oluşur
./mvnw clean install

# Sadece backend modülünü çalıştır
./mvnw -pl backend spring-boot:run

# Tüm testleri çalıştır
./mvnw test

# Sadece backend test'leri
./mvnw -pl backend test

# Test'leri atlayarak hızlı package
./mvnw clean package -pl backend -DskipTests -Drevision=0.0.2

# Bağımlılıkları offline modda indirme (CI cache için)
./mvnw dependency:go-offline -B
```

### NPM (frontend/ dizininde)

```bash
cd frontend
npm install
npm run dev       # Dev server: http://localhost:3000
npm run build     # tsc -b && vite build -> dist/
npm run lint      # oxlint
npm run preview   # Build çıktısını lokal serve et
```

### Test Çalıştırma Öncesi Kontrol Listesi

- [ ] H2/dev profili ile uygulama ayağa kalkıyor mu?
- [ ] `./mvnw test` yeşil mi?
- [ ] `npm run lint` uyarı vermiyor mu?
- [ ] Yeni kod ilgili test'i içeriyor mu?

---

## Frontend Geliştirme

### Teknoloji
- **React 19** + **TypeScript** + **Vite**
- **Server State:** TanStack Query v5 (API çağrıları, cache, retry)
- **Client State:** Zustand v5 (auth state, UI state)
- **Styling:** Tailwind CSS (mevcut custom CSS Faz 4'te Tailwind'e taşınacak)
- **Test:** Vitest + React Testing Library (unit/component), Playwright (E2E)
- **Lint:** ESLint 9 (Flat Config) veya Oxlint — `.oxlintrc.json` konfigürasyonu
- Font: Outfit + Inter (Google Fonts üzerinden)

### Dev Server & Proxy
`vite.config.ts` içinde `/api` istekleri `http://localhost:8080`'e proxy edilir. Backend ayrı çalışıyorken CORS problemi yaşanmaz.

### Build Çıktısı
`npm run build` → `frontend/dist/` üretir. Maven build sırasında `backend/src/main/resources/static/` altına kopyalanır. Bu yüzden **backend değişikliği olmadan frontend değişikliği production jar'a yansımaz** — kök `./mvnw install` ile tüm modüller build edilmeli.

### Uyarılar
- Mevcut `App.tsx` **mock veri** (`TENANT_DATA`) kullanıyor. Gerçek API entegrasyonu Faz 4'te yapılacak.
- `err: any` kullanımı mevcut — tip güvenliği Faz 4'te düzeltilecek.

---

## Backend Geliştirme

### Teknoloji
- **Spring Boot**, **Java 21**
- **Spring Data JPA** (Hibernate) + **Flyway** (per-schema migration)
- **Spring Security** + **JWT (jjwt)** — access + refresh token, Redis blacklist
- **Redis 7.4** — cache, rate limiting, token blacklist
- **PostgreSQL** 16 (prod) / **H2** (dev/test) / **Testcontainers** (integration testleri)
- **Lombok** (sadece backend modülünde)
- Statik analiz: Checkstyle / PMD

### Endpoint Sözleşmesi
- Tüm endpoint'ler `/api/v1/*` prefix'i altında.
- Response'lar DTO (tercihen `record`) olarak dönmeli, entity'ler direkt expose edilmemeli.
- Hata yanıtları tek tip `ErrorResponse` formatında (`GlobalExceptionHandler` tarafından):
  ```json
  {
    "status": 400,
    "error": "Bad Request",
    "message": "...",
    "path": "/api/v1/...",
    "timestamp": "2026-07-06T..."
  }
  ```

### Tenant Context Kuralları
- `TenantContext` (ThreadLocal) **`common` modülündedir** — hem web katmanı (backend'deki `TenantFilter`) hem de persistence katmanı (connection provider) tarafından ortak kullanılır.
- Her istek `TenantFilter`'dan geçer, subdomain'i parse edip tenant context'e yazar.
- Service katmanında `TenantContext.getCurrentTenant()` ile okunur (backend → common dependency'si ile).
- **Asla** controller'da tenant doğrulama yapma — filter servisler için tek sorumluluk alanı.
- Yeni async thread'ler (örn. `@Async`) başlatılınca ThreadLocal taşıınmaz — explicit propagate etmek gerekir (Faz 1'de decision needed).

### Logging
- SLF4J + Logback (Spring Boot default).
- Paket bazlı log seviyeleri `application.yaml` veya profile dosyasında ayarlanır.
- Hassas veri (token, şifre, PII) ASLA log'a yazılmaz.

---

## Deployment

### Docker (mevcut yöntem)

```bash
# Tam yeniden build + ayağa kaldırma
docker-compose up --build -d

# Logları izle
docker-compose logs -f app

# Durdur
docker-compose down

# DB volume'ını da sil (dikkat: veri kaybı)
docker-compose down -v
```

### Dockerfile Akışı (3 stage)

1. **frontend-builder** (`node:20-alpine`): `npm ci` → `npm run build` → `/app/frontend/dist`
2. **backend-builder** (`maven:3.9.6-eclipse-temurin-21-alpine`): Bağımlılıkları indir → frontend dist'i `static/` altına kopyala → `mvn package` → `systemforge-backend.jar`
3. **runtime** (`eclipse-temurin:21-jre-alpine`): Root olmayan `systemuser` ile jar'ı çalıştır. Port 8080.

### Güvenlik Notları
- Runtime container root olarak çalışmaz (`systemuser:systemgroup`).
- Prod DB credential'ları `docker-compose.yml` içinde plain-text — **production için secret manager (Vault, AWS Secrets Manager) veya `.env` + gitignore** kullanılmalı.

### Production Öncesi Checklist (Faz 5)
- [ ] `application-prod.yaml` oluşturuldu
- [ ] `ddl-auto=validate` + Flyway migration
- [ ] Secret'lar repo dışında
- [ ] HTTPS termination (reverse proxy / nginx / load balancer)
- [ ] Health check endpoint (`/actuator/health`)
- [ ] Log aggregation (ELK / Loki / CloudWatch)
- [ ] CI/CD pipeline

---

## Troubleshooting

### `mvnw: Permission denied`
```bash
chmod +x mvnw
```

### Backend ayağa kalkıyor ama frontend static servis etmiyor
Frontend build çıktısı `backend/src/main/resources/static/` altında olmalı. Çözüm:
```bash
./mvnw clean install   # tüm modülleri yeniden build eder
```

### Port 8080 / 3000 / 5432 kullanımda
```bash
# Hangi proces kullanıyor?
lsof -i :8080
# Durdur ve tekrar dene
```

### Docker container DB'ye bağlanamıyor
- `docker-compose up db` ile önce DB'yi单独 ayağa kaldır, `pg_isready` kontrol et.
- Volume eski versiyondan kaldıysa: `docker-compose down -v` (veri gider).

### H2 Console'a erişim (dev)
`application.yaml`'a ekle (geçici):
```yaml
spring:
  h2:
    console:
      enabled: true
```
Sonra: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:systemforge`)

### Frontend "Offline Mode" gösteriyor
Backend çalışmıyor demektir. Backend'i başlat veya mock veriyle devam et (normal davranış).

### `npm ci` Docker build'de fail ediyor
`frontend/package-lock.json` commit edilmiş mi kontrol et. `package.json` değiştiyse lokalde `npm install` çalıştırıp lock dosyasını güncellemek gerekir.

---

## Katkı Sağlama

1. `develop` dalından feature branch aç.
2. Conventional Commits formatına uy.
3. PR açmadan önce: `./mvnw test` + `npm run lint` çalıştır.
4. Tenant izolasyonu içeren değişikliklerde ekstra dikkat — başkasının tenant'ına sızan veri en kritik bug sınıfıdır.
5. PR aç, review bekle, squash merge.

Sorular için `AGENT.md` içindeki açık kararlar ve yol haritası bölümüne bakabilirsin.
