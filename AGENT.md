# AGENT.md

> Bu dosya, SystemForge projesinin amacını, mimarisini, mevcut durumunu ve ilerleyişini takip etmek için kullanılır. Her özellik/değişiklik tamamlandığında ilgili bölümler güncellenmelidir.

## 1. Proje Amacı

**SystemForge**, çok kiracılı (multi-tenant) bir SaaS platformudur.

- Şirketler (tenant) sisteme kayıt olur ve kendi çalışma alanlarını (workspace) oluşturur.
- Her tenant kendi **takımlarını**, **projelerini** ve **görevlerini** (task) yönetir.
- Kullanıcılar **Role-Based Access Control (RBAC)** ile sisteme giriş yapar ve yetkileri ölçüsünde işlem yapar.
- Tenant'lar birbirinden tamamen izoledir (veri sızıntısı olmamalı).

## 2. Teknoloji Yığını

> Versiyonlar güncel sürümlere göreayarlanmalı; buradakiler hedef yönü gösterir. Gerçek sürümler modül pom'larında netleşecek.

### Backend (Java & Spring)
- **Core:** Java 21 (LTS), Spring Boot (mevcut pom'da 4.1.0 — güncel LTS'e sabitlenecek)
- **Veritabanı & ORM:** PostgreSQL 16, Spring Data JPA + Hibernate
- **Cache / Rate Limit / Token Blacklist:** Redis 7.4
- **DB Migration:** Flyway (per-schema migration — yeni tenant şeması oluşturmak için)
- **Güvenlik:** Spring Security, **JWT (jjwt)** — OAuth2 Resource Server opsiyonel
- **Test:** JUnit 5, Mockito, **Testcontainers** (gerçek PostgreSQL ile schema-per-tenant integration testleri)
- **Maven** multi-module + Maven Wrapper (`mvnw`)
- **Lombok** (sadece backend modülünde)
- **Statik analiz:** Checkstyle / PMD

### Frontend (React & TypeScript)
- **Core:** React 19, TypeScript, Vite
- **Server State (data fetching):** TanStack Query v5
- **Client State:** Zustand v5
- **Styling:** Tailwind CSS (mevcut custom CSS yeniden yazılacak)
- **Test:** Vitest, React Testing Library, Playwright (E2E)
- **Lint:** ESLint 9 (Flat Config) veya Oxlint (mevcut)

### DevOps & Araçlar
- **Konteyner:** Docker, Docker Compose v2
- **CI/CD:** GitHub Actions
- **Runtime:** `eclipse-temurin:21-jre-alpine` (non-root user)

### Altyapı / Deployment
- **Docker** multi-stage build (frontend build → backend static → runtime jar)
- **docker-compose:** `db` (postgres:16-alpine) + `app` (systemforge-app, `:8080`) — **Redis servisi eklenecek**
- Tek self-contained jar olarak çalışır (frontend gömülü)

## 3. Mimari

```
systemforge (parent pom, packaging=pom, revision=0.0.2)  ← sadece AGGREGATOR
├── common/      → Paylaşılan çekirdek (DTO, exception, TenantContext, yardımcılar)
│                  Bağımlılık profili: MINIMAL (Spring yok, JPA yok)
├── persistence/ → JPA entity'leri, repository'ler, multi-tenancy altyapısı
│                  Bağımlılık profili: JPA + Hibernate + common
├── backend/     → Spring Boot uygulaması (controller, service, security, main)
│                  Bağımlılık profili: Spring Boot starters + common + persistence
│                  src/main/resources/static/ ← frontend build çıktısı buraya kopyalanır
└── frontend/    → React + Vite (Maven ile node install + build yapar, pom packaging=pom)
```

**Modül bağımlılık grafiği (döngüsüz):**
```
common      ← (bağımlılık yok)
   ↑
persistence ← common
   ↑
backend     ← common + persistence
frontend    ← (bağımlılık yok, bağımsız npm build)
```

- Frontend `frontend-maven-plugin` ile lokal Node v26 / npm 11.12.1 üzerinden build alınır.
- Build çıktısı (`dist/`) `maven-resources-plugin` ile `backend/src/main/resources/static/` altına kopyalanır.
- Sonuç: tek jar, tek port (`:8080`), tek container. Sadece `backend` modülü executable jar üretir; `common` ve `persistence` kütüphane jar'ıdır.

### Neden bu modül ayrımı?
- **common:** Web ve persistence katmanlarının ortak kullandığı tip (örn. `TenantContext`) burada. Spring bağımlılığı yok → testi kolay, lightweight.
- **persistence:** Veri erişimi izole. Multi-tenancy connection provider burada. Backend controller'dan bağımsız gelişebilir/test edilebilir.
- **backend:** Sadece orchestration (HTTP → service → repository). Domain altyapısından arınmış.
- Kök pom'un aggregator kalması bu ayrım için ZORUNLU — her modülün farklı bağımlılık profili var, hepsine dayatma yapılamaz.

### Multi-Tenancy Stratejisi (KARAR VERİLDİ — hedef)
- **Veri izolasyonu:** Schema-per-tenant. Her tenant'a PostgreSQL'de ayrı bir şema (örn. `tenant_acme`) ayrılır. Tenant'lar arası tam şema izolasyonu.
- **Tenant çözümleme:** Subdomain tabanlı. `acme.systemforge.com` → tenant = `acme`.
- **Uygulama yolu:** Hibernate çok-kiracılı desteği → `MultiTenantConnectionProvider<String>` + `CurrentTenantIdentifierResolver<String>` ile runtime'da şema seçimi.
- **Master/Shared şema:** Tenant kayıt listesi, global kullanıcılar, billing gibi paylaşılan veriler ayrı bir `public`/`master` şemada tutulur. Tenant'a özel iş verileri (project, task, team) her tenant'ın kendi şemasında.
- **Dev notu:** Lokalde subdomain testi için `acme.localhost:3000` (DNS'siz çözümlenir) veya `acme.nip.io` kullanılabilir.

### Mevcut Kodun Durumu (AI tarafından otomatik üretildi — DEĞİŞECEK)
- `TenantFilter` + `TenantContext` **header-tabanlı (`X-Tenant-ID`) ve shared-schema** varsayımıyla yazılmış.
- Hedef mimari (subdomain + schema-per-tenant) ile uyumlu DEĞİL.
- `TenantContext` (ThreadLocal) fikri korunabilir ama `TenantFilter` subdomain parse edecek şekilde baştan yazılır.
- Header-tabanlı test endpoint'i ve mock frontend verisi geçici olarak kalabilir, gerçek akış oturunca kaldırılır.

### Paket Yapısı (modüller bazında)

**`common` modülü** — `com.ibrhalil.systemforge.common.*`
```
├── tenant/    → TenantContext (ThreadLocal<String>)
├── exception/ → TenantNotFoundException, paylaşılan exception'lar
├── dto/       → ErrorResponse gibi paylaşılan DTO'lar (record)
└── util/      → yardımcı sınıflar, sabitler
```

**`persistence` modülü** — `com.ibrhalil.systemforge.persistence.*`
```
├── tenant/       → MultiTenantConnectionProvider, CurrentTenantIdentifierResolver
├── config/       → JPA/Hibernate çok-kiracılı konfigürasyon
├── domain/
│   ├── master/   → Tenant, User, TenantMembership, Role (public şema)
│   └── tenant/   → Project, Task, Team (her tenant'ın kendi şeması)
└── repository/   → Spring Data JPA repository'leri
```

**`backend` modülü** — `com.ibrhalil.systemforge.*`
```
├── SystemforgeApplication.java   (main)
├── tenant/      → TenantFilter (subdomain parse), TenantResolver
├── controller/  → REST controller'ları (/api/v1/*)
├── service/     → iş mantığı
├── security/    → Spring Security, JWT/filter, RBAC
└── config/      → uygulama konfigürasyonu, application.yaml
```

## 4. Geliştirme Komutları

### Backend (kök dizinden)
```bash
./mvnw clean install                 # tüm modülleri build et (frontend dahil)
./mvnw -pl backend spring-boot:run   # sadece backend'i çalıştır (H2 ile)
./mvnw test                          # testleri çalıştır
```

### Frontend (`frontend/` dizininde)
```bash
npm install
npm run dev        # http://localhost:3000 (API'yi :8080'e proxy eder)
npm run build      # tsc + vite build → dist/
npm run lint       # oxlint
```

### Docker (tam stack)
```bash
docker-compose up --build      # db + app birlikte ayağa kalkar
# app: http://localhost:8080  |  db: localhost:5432
```

### Lokal profil notları
- `application.yaml` environment variable'larla yapılandırılmıştır; tanımsızsa H2 (in-memory) varsayılan.
- Prod DB credential'ları `docker-compose.yml` içinde (`forgeadmin` / `forgepassword`).
- `SPRING_PROFILES_ACTIVE=prod` Dockerfile'da set edilmiştir ancak henüz `application-prod.yaml` YOK.

## 5. Mevcut Durum (v0.0.2)

### Tamamlananlar
- [x] Maven multi-module proje iskeleti (parent + backend + frontend)
- [x] Spring Boot temel uygulama (`SystemforgeApplication`)
- [x] Multi-tenancy altyapısı: `TenantContext` (ThreadLocal) + `TenantFilter` (header-based — geçici)
- [x] Test endpoint: `GET /api/v1/tenant-test` (header'dan tenant'ı doğrular)
- [x] Global exception handling (`@RestControllerAdvice` + `ErrorResponse` record)
- [x] TenantContext için birim testleri
- [x] React dashboard iskeleti (sidebar, tab yapısı, tenant selector)
- [x] Docker multi-stage Dockerfile + docker-compose (PostgreSQL + app)
- [x] Frontend → backend build entegrasyonu (static kopyalama)
- [x] **Faz 1 (modül iskeleti):** Root pom saf aggregator'a çevrildi. `common` ve `persistence` modülleri oluşturuldu. `TenantContext` + `TenantNotFoundException` common'a taşındı. Bağımlılık grafiği: common ← persistence ← backend. `.mvn/maven.config` ile `revision` paylaşımı. Build + testler yeşil.

### Eksik / Henüz Yok
- [ ] Domain entity'leri (Tenant, User, Team, Project, Task, Role, Permission)
- [ ] Repository / Service / gerçek Controller katmanları
- [ ] Veritabanı şeması & migration stratejisi (şu an `ddl-auto=update` — production için flyway/liquibase gerekli)
- [ ] Tenant doğrulama (`TenantFilter` context'i set ediyor ama DB'den validate etmiyor)
- [ ] Kimlik doğrulama & yetkilendirme (Spring Security, JWT/Session, RBAC)
- [ ] Kullanıcı kayıt/giriş akışı (auth endpoints + frontend login)
- [ ] Tenant kayıt / workspace oluşturma akışı
- [ ] Veri izolasyonu (tüm sorgular tenant-aware olmalı)
- [ ] API dokumentasyonu (OpenAPI/Swagger)
- [ ] `application-prod.yaml` / `application-dev.yaml` profilleri
- [ ] CI/CD pipeline
- [ ] Frontend: mock veri yerine gerçek API entegrasyonu (şu an `TENANT_DATA` hardcoded)
- [ ] Test kapsamı (sadece TenantContext ve contextLoads testi var)
- [ ] Logging/Monitoring stratejisi

## 6. Yol Haritası

> Faz 0 neredeyse tamamlandı. Auth kararı tech stack analiziyle netleşti (JWT + Redis blacklist). Birkaç açık karar sonra Faz 1 başlayacak.

### Faz 0 — Tasarım & Kararlar (TAMAMLANDI ✅)
- [x] Multi-tenancy: **schema-per-tenant**
- [x] Tenant resolution: **subdomain**
- [x] Teknoloji yığını netleştirildi (Redis, Flyway, Testcontainers, TanStack Query, Zustand, Tailwind)
- [x] **Auth stratejisi:** JWT (jjwt) access + refresh token, **Redis ile token blacklist** (logout/revocation için)
- [x] **Kullanıcı ↔ tenant ilişkisi:** Multi-membership modeli (Slack/Notion gibi). Bir kullanıcı birden fazla tenant'a üye olabilir. Master şemada `users` + `tenant_memberships` tabloları.
- [x] **Master şema yeri:** Ayrı `public` şema (tenant registry, global users, memberships, billing)
- [x] **Root domain davranışı:** `systemforge.com` → landing/login, `app.systemforge.com` → tenant seçici, `acme.systemforge.com` → workspace
- [x] **Schema oluşturma stratejisi:** Flyway per-schema (Hibernate `ddl-auto=validate`'e geçilecek)

### Faz 1 — Modül İskeleti + Multi-Tenancy Altyapısı (mevcut kod değişecek)
1. ✅ **Root pom lightweight parent oldu:** `spring-boot-starter-parent` hiçbir modülde parent değil. Spring Boot BOM root `<dependencyManagement>`'da. Version'lar root `<properties>`'te.
2. ✅ **`common` modülü oluşturuldu** — `TenantContext` + `TenantNotFoundException` buraya taşındı. Spring/JPA bağımlılığı YOK.
3. ✅ **`persistence` modülü oluşturuldu** — pom.xml + paket iskeleti (`tenant/`, `config/`, `domain/master/`, `domain/tenant/`, `repository/`). JPA bağımlılığı eklendi.
4. ✅ **`backend` modülü** `common` + `persistence`'a bağımlı. `spring-boot-starter-parent` backend'in kendi parent'ı.
5. ✅ **Concrete version paylaşımı:** `${revision}` yerine concrete `0.0.2` tüm modüllerde. flatten-maven-plugin gereksiz olduğu için eklenmedi (publish pipeline ihtiyacı doğunca).
6. ✅ **Build + testler yeşil** (common: 2 test, backend: 1 test).
7. [ ] Redis servisini `docker-compose.yml`'ye ekle (Faz 2'de kullanılmak üzere)
8. [ ] `TenantFilter` → subdomain parse edecek şekilde yeniden yaz
9. [ ] Hibernate `MultiTenantConnectionProvider` + `CurrentTenantIdentifierResolver` (persistence modülünde)
10. [ ] Master şema entity'leri: `Tenant` (id, subdomain, schema_name, status)
11. [ ] Tenant doğrulama (subdomain → master şemadan lookup)
12. [ ] Flyway per-schema kurulumu + yeni tenant signup → yeni PostgreSQL şeması oluşturma akışı
13. [ ] `TenantTestController` kaldırılır, gerçek endpoint'ler gelir

### Faz 2 — Kimlik Doğrulama & RBAC
12. Spring Security + JWT (jjwt) entegrasyonu
13. Access + Refresh token akışı, Redis token blacklist
14. Kullanıcı kayıt/giriş endpoint'leri
15. Rol/Permission modeli (`OWNER`, `ADMIN`, `MEMBER`, `VIEWER`)
16. Method-level yetkilendirme (`@PreAuthorize`)
17. Rate limiting (Redis tabanlı)

### Faz 3 — İş Modülü (her tenant'ın kendi şemasında)
18. Master şema: `User`, `TenantMembership` (Tenant Faz 1'den geliyor)
19. Tenant şeması: `Team`, `Project`, `Task`
20. CRUD endpoint'leri (hepsi tenant-scoped + RBAC)
21. Testcontainers ile integration testleri

### Faz 4 — Frontend Entegrasyonu
13. Mock veriyi kaldır, gerçek API çağrıları
14. Login/kayıt + auth state yönetimi
15. Subdomain kullanan tenant workspace yönlendirmesi
16. Proje & görev yönetim ekranları

### Faz 5 — Hardening & Operasyon
17. Profiller (`dev`, `prod`, `test`)
18. Flyway per-schema migration
19. CI/CD (GitHub Actions)
20. Observability, API dokümantasyonu (springdoc-openapi)
21. Integration + E2E testleri

## 7. Önemli Notlar & Kurallar

- **Root pom lightweight parent + aggregator'dır.** Modüllere dependency DAYATMAZ (`<dependencies>` yok) ama merkezi version management sağlar (`<dependencyManagement>` + `<pluginManagement>` + `<properties>`). Bu enterprise standardı — Spring Boot BOM import'u version merkezidir, modüllere Spring dayatmaz.
  - Tüm modüller root'u parent olarak kullanır. Hiçbir modül `spring-boot-starter-parent`'ı parent yapmaz — Spring Boot BOM root'un `<dependencyManagement>`'ında import edilir.
  - Version bump tek yerden: root `<properties>` (`spring-boot.version`, `java.version`). Sürümler modül pom'larında tekrarlanmaz.
  - Modüller sadece ihtiyaç duydukları bağımlılığı deklare eder (version olmadan). `common` Spring'siz kalır çünkü `dependencyManagement` = dayatma değildir, sadece version yönetimidir.
- **Commit öncesi:** lint ve testleri çalıştır (`./mvnw test` + `npm run lint`).
- **Tenant izolasyonu** her yeni entity/query için ZORUNLU — hiçbir sorgu tenant filtresiz olmamalı.
- **Secret yönetimi:** DB şifreleri ve hassas bilgiler repo'ya commit edilmez; env variable kullan.
- **Build:** frontend değişiklikleri `static/` altına kopyalanmadan backend'e yansımaz — kök `mvn install` ile tüm modüller build edilmeli.
- **Dallanma:** `main` (prod) + `develop` (geliştirme) mevcut; özellikler feature branch'inde geliştirilmeli.
- **Kod stili:** Backend'de paket = `com.ibrhalil.systemforge.*`, record tabanlı DTO'lar, `@RestControllerAdvice` ile merkezi hata yönetimi, Lombok kullanımı yaygın.

## 8. Değişiklik Geçmişi

| Tarih | Sürüm | Not |
|-------|-------|-----|
| 2026-07-06 | 0.0.2 | AGENT.md oluşturuldu. İlk analiz varsayımlıydı. |
| 2026-07-06 | 0.0.2 | **Tasarım kararları güncellendi:** Multi-tenancy = schema-per-tenant, tenant resolution = subdomain. Mevcut kod (header+shared-schema) değişecek olarak işaretlendi. Auth kararı bekliyor. |
| 2026-07-06 | 0.0.2 | **Root pom kuralı eklendi:** Sadece aggregator'dır, parent davranışı yok. Lombok/Java ayarları backend'e taşınacak. |
| 2026-07-06 | 0.0.2 | **Modül yapısı genişletildi:** `common` ve `persistence` modülleri planlandı. Bağımlılık grafiği: common ← persistence ← backend. |
| 2026-07-06 | 0.0.2 | **Tech stack netleştirildi:** Redis (cache + token blacklist), Flyway (per-schema), Testcontainers, TanStack Query, Zustand, Tailwind, Playwright eklendi. Auth = JWT + Redis blacklist olarak kararlaştırıldı. Sağlık skoru bölümü eklendi (~%15-20 ilerleme). |
| 2026-07-06 | 0.0.2 | **Faz 0 tamamlandı:** Multi-membership (kullanıcı birden fazla tenant'a üye), root domain davranışı, master şema yeri kararları verildi. |
| 2026-07-06 | 0.0.2 | **Faz 1 modül iskeleti tamamlandı:** Root pom saf aggregator'a çevrildi. `common` (TenantContext + exception) ve `persistence` (JPA) modülleri oluşturuldu. Bağımlılık grafiği: common ← persistence ← backend. Concrete version `0.0.2` tüm modüllerde. Build + test yeşil. |
| 2026-07-06 | 0.0.2 | **flatten-maven-plugin kaldırıldı:** `${revision}` pattern'i yerine concrete version kullanılıyor. Publish/release pipeline ihtiyacı doğunca eklenecek. |
| 2026-07-06 | 0.0.2 | **Root pom lightweight parent'a çevrildi (enterprise pattern):** Tüm modüller root'u parent yapar. Spring Boot BOM `<dependencyManagement>`'da import. Version'lar root `<properties>`'te merkezileştirildi. Modül pom'larında version YOK. `spring-boot-starter-parent` artık hiçbir modülün parent'ı değil. |

## 9. Açık Sorular (Kullanıcıya Sorulacak)

Faz 0 tamamlandı — açık soru kalmadı. Yeni kararlar gerektikçe buraya eklenecek.

## 10. Mevcut Kodun Sağlık Skoru (analiz raporu özeti)

| Kategori | Puan | Not |
|----------|------|-----|
| Proje iskeleti & build pipeline | 8/10 | Docker pipeline çalışıyor |
| Dokümantasyon | 9/10 | AGENT.md + DEVELOPMENT.md detaylı |
| Docker & deployment | 7/10 | Çalışıyor, Redis eklenecek |
| Frontend (UI iskeleti) | 5/10 | Mock veri, tek dosya (App.tsx ~400 satır) |
| Backend iş mantığı | 2/10 | Sadece test endpoint + exception altyapısı |
| Multi-tenancy (schema-per-tenant) | 2/10 | Sadece ThreadLocal stub (header-based) |
| Modül ayrımı (common/persistence/backend) | 1/10 | Henüz uygulanmadı |
| Auth & Security | 0/10 | Yok |
| Test kapsamı | 2/10 | Sadece TenantContext + contextLoads |

**Toplam ilerleme:** ~%15-20 (hedef mimariye göre)

### Kritik Bloklayıcılar (Faz 1 öncesi çözülmeli)
1. ~~**Root pom refaktörü**~~ ✅ Tamamlandı — spring-boot-starter-parent + Lombok root'tan kaldırıldı.
2. ~~**Modül ayrımı**~~ ✅ Tamamlandı — `common` ve `persistence` oluşturuldu, bağımlılık grafiği kuruldu.
3. **Sonraki bloklayıcı:** Multi-tenancy altyapısı (subdomain filter + Hibernate schema-per-tenant provider) — Faz 1'in kalan adımları.
