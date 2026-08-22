# ForgeSys — Tam Kapsamlı Proje Analizi

> **Tarih:** 2026-08-22 · **Kapsam:** Sistemin tam anlaşılması, mimari eleştiri, sadeleştirme planı, 2026 standartları değerlendirmesi, geliştirme süreci standardizasyonu.
> **Amaç:** Bu doküman, projeye yeni dahil olan bir developer'ın sistemi anlaması ve gelecekteki geliştirme kararlarının bu analizle tutarlı olup olmadığının kontrol edilmesi için **referans kaynak (source of truth)** olarak yazılmıştır.
> **Durum notu:** Bu analiz bir planlama session'ının çıktısıdır — analiz sırasında **kod değiştirilmemiştir**. Uygulama kararları ayrı session'larda alınacaktır.
> **Temel prensip:** *Complexity is a cost. Introduce complexity only when it solves a real problem.*

---

## İçindekiler

1. [Proje ne yapıyor?](#1-proje-ne-yapıyor)
2. [Nasıl çalışıyor?](#2-nasıl-çalışıyor)
3. [Mevcut mimari neden bu şekilde?](#3-mevcut-mimari-neden-bu-şekilde)
4. [Hangi kısımlar iyi ve korunmalı?](#4-hangi-kısımlar-iyi-ve-korunmalı)
5. [Hangi kısımlar gereksiz veya aşırı karmaşık?](#5-hangi-kısımlar-gereksiz-veya-aşırı-karmaşık)
6. [Neleri sadeleştirmeliyiz?](#6-neleri-sadeleştirmeliyiz)
7. [Neleri değiştirmemeliyiz?](#7-neleri-değiştirmemeliyiz)
8. [2026 standartlarına göre eksikler](#8-2026-standartlarına-göre-eksikler)
9. [Yeni mimari / yaklaşım nasıl olmalı?](#9-yeni-mimari--yaklaşım-nasıl-olmalı)
10. [Bundan sonraki feature geliştirme süreci](#10-bundan-sonraki-feature-geliştirme-süreci)
11. [Standartlaştırılmış kararlar (tekrar tartışılmayacak)](#11-standartlaştırılmış-kararlar-tekrar-tartışılmayacak)
12. [Güncellenmesi gereken dokümanlar](#12-güncellenmesi-gereken-dokümanlar)
13. [Uygulama öncesi tamamlanması gereken kararlar](#13-uygulama-öncesi-tamamlanması-gereken-kararlar)
14. [Önceliklendirilmiş aksiyon planı](#14-önceliklendirilmiş-aksiyon-planı)

---

## 1. Proje ne yapıyor?

**ForgeSys** — modüler, çok-kiracılı (multi-tenant) SaaS platformu.

**Temel amaç:** Şirketler (tenant) kendi ekiplerini yönetebilecekleri, ihtiyaç duydukları **modülleri** açabilecekleri (Tasks, Notes, Warehouse, Logistics) ve kendi **custom application'larını** (Notion/Airtable tarzı JSONB EAV modeli) yaratıp yönetebilecekleri tek bir platform.

**Hibrit ürün modeli:**
- **Built-in modüller** (Odoo/ERPNext mantığı): Projects & Tasks (`pm` modülü — DONE), Notes, Warehouse, Logistics (planlandı)
- **Custom App Builder** (Notion/Airtable mantığı): Tenant'ların kendi veri modellerini (property: TEXT/NUMBER/SELECT/DATE/USER/RELATION), view'lerini (TABLE/BOARD/CALENDAR/GALLERY/LIST), record'larını yönetebildiği dinamik uygulama builder'ı (`apps` modülü — backend DONE, UI Faz 4.2)

**Kimler kullanır?**
- **Tenant adminleri:** Kendi şirketlerinin kullanıcı/rol/grup/izin yönetimini, modül aktivasyonunu yapar
- **Platform adminleri (system tenant):** Tüm tenant'ları görür, durum değiştirir (SUSPEND/TERMINATE) — `/api/v1/platform/companies`
- **Son kullanıcılar:** Tasks, custom app'ler, projeler üzerinden iş yapar; self-service profil/şifre yönetimi

**Temel kullanıcı senaryoları:**
1. **Signup (K-21 iki fazlı):** Şirket kaydı → `PROVISIONING` Company + doğrulama token'ı → mail linki → verify → şema + Flyway + admin user → `ACTIVE`
2. **Login:** Subdomain üzerinden → JWT access (15 dk, cookie) + opaque refresh (7 gün, Redis) → yetkiler token'a gömülü
3. **RBAC yönetimi:** User/Role/Permission/Group CRUD + atama + effective-permissions görüntüleme + rol kalıtımı
4. **Modül aktivasyonu (K-16):** Plan bazlı (FREE/PRO/ENTERPRISE) — plan gate → modül Flyway migration → permission seed → aktivasyon kaydı
5. **Custom App Builder (K-15):** App yaratma → property/view tanımlama → record CRUD + JSONB search (native PG)
6. **Audit/Log (K-19):** Admin aksiyonları (append-only) + login geçmişi + request trace

---

## 2. Nasıl çalışıyor?

### 2.1 Request Lifecycle (HTTP)

```
Browser → Vite Dev Server (:3000, /api proxy)
    → Spring Boot (:8080)
        → RequestMetadataFilter (-102): traceId (X-Request-Id/UUID), client IP, User-Agent → MDC + RequestContext
        → TenantFilter (-101): Host header → subdomain → CompanyRepository.findBySubdomain
            → schemaName → TenantContext.set() (yalnız ACTIVE tenant'lar çözümlenir)
        → SecurityFilterChain (-100):
            → RateLimitFilter (public auth endpoint'leri, JWT decode'dan önce)
            → JwtAuthenticationFilter:
                → Cookie/Authorization header'dan access token → RS256 decode
                → iss/aud doğrulama
                → tenant claim == TenantContext (RISK-19 cross-tenant escalation kapalı)
                → tokenInvalidBefore kontrolü (user-scoped revoke, RISK-21)
                → jti blacklist kontrolü (granular revoke, K-34)
                → CustomUserDetails principal + authorities → SecurityContext
        → Controller (@PreAuthorize enforcement — K-26)
        → Service (@Transactional)
        → Hibernate Session → TenantIdentifierResolver → TenantContext.get()
        → SchemaPerTenantConnectionProvider → SET search_path TO tenant_xxx, public
        → DB Query
        → Response → TenantContext.clear() (finally — ThreadLocal leak yok)
```

### 2.2 Multi-Tenancy: Schema-per-Tenant

- Tek PostgreSQL cluster, tek connection pool
- Her tenant fiziksel ayrı şemada: `tenant_<subdomain>`
- **`public` şema:** `t_companies`, `t_organization_domains`, `t_tenant_verification_tokens`, `t_plans`, `t_subscriptions`, `t_tenant_modules`
- **Tenant şeması:** User/Role/Permission/Group + join tabloları + audit/log + modül tabloları (`t_projects`/`t_tasks` pm; `t_apps` ailesi apps)
- Şema adı regex `^[a-z0-9_]+$` ile doğrulanır (SQL injection savunması)
- Tenant context null → resolver `"public"` döner
- `@Async` thread'lerde `TenantContext` taşınmaz (RISK-10; şu an consumer yok, defer)

### 2.3 Authentication & Authorization

| Katman | Teknoloji | Detay |
|--------|-----------|-------|
| Access token | RS256 JWT (15 dk) | `sf_access_token` httpOnly cookie + body; claims: sub/email/tenant/authorities/jti |
| Refresh token | Opaque (7 gün) | `sf_refresh_token` httpOnly cookie (`Path=/api/v1/auth`); Redis SHA-256 hash-at-rest; atomik Lua rotasyon + reuse detection |
| Revoke | İki katmanlı | 1) `tokenInvalidBefore` (user-scoped — password change/reset/reuse/lock) 2) `jti` blacklist (per-session logout) |
| Privilege-change revoke | `SessionRevocationService` | Rol/izin/grup değişiminde etkilenen kullanıcıların token'ları anında düşer (privilege-retention penceresi kapalı) |
| Brute-force | Login-scoped | 5 deneme / 15 dk → `lockedUntil` + `tokenInvalidBefore` stamp (lock anında token'lar düşer); 423 `auth_account_locked` |
| Rate limiting | Redis Lua token-bucket | `/auth/login` + `/auth/company/verify` + `/auth/refresh`; tenant+IP bazlı; JWT decode'dan önce |
| Şifre saklama | PepperingPasswordEncoder | HMAC-SHA256 pepper pre-hash + BCrypt(12); `{sf-peppered}` marker; lazy rehash (K-23) |
| RBAC | `@PreAuthorize` + `@EnableMethodSecurity` | Namespace `{module}:{resource}:{action}` — `iam:*`, `platform:*`, `pm:*`, `apps:*` |
| Authority resolution | DB-driven (query) | Direct roles + active group roles → transitive parent inheritance (BFS, `t_role_parents`) → `all_permissions` flag short-circuit (`PermissionRepository.findAllNames`) |
| Admin model | `all_permissions` flag (K-35) | Admin implicit süper-kullanıcı; explicit grant satırı yok; runtime permission'lar otomatik ulaşır |

### 2.4 Frontend-Backend Sözleşme

- **API prefix:** `/api/v1/*`
- **Sayfalama:** `PageResponse<T>` = `{data[], meta: {page, pageSize, totalElements, totalPages, hasNext, hasPrevious}}` (0-based)
- **Hata:** `ApiErrorResponse` = `{timestamp, status, error, code, message, path, traceId, fields[]}` — `code` = stable `ErrorCode` (lowercase enum); client `code` üzerinde branch eder
- **Sort/filter:** Whitelist tabanlı — `SortGuard` + `FilterFieldSet` + JPA metamodel sabitleri (`User_.EMAIL` — rename build'i kırar); 14 operatör; hard limitler (≤10 filter, ≤5 sort, IN ≤100, size ≤100)
- **Search:** GET listelerde `?q=` (global OR-CONTAINS); `POST /users/search` full-body referans endpoint
- **Auth transport:** Cookie-based; `lib/api.ts` transparent 401→refresh→retry (concurrent 401'ler tek `/refresh`'te coalesce)
- **DataIntegrityViolation** → constraint name substring map → 400 `*_TAKEN` (TOCTOU race, RISK-28)

### 2.5 Frontend State Management

| Store | Amaç |
|-------|------|
| `authStore` (Zustand) | Session (user, authorities), login/fetchMe/logout, `hasAuthority()` — UI gate; backend gerçek security'yi enforce eder |
| `tenantStore` (Zustand) | `X-Tenant-ID` header (localStorage `sf_tenant_id` veya subdomain detection; dev-profile fallback) |
| `localeStore` (Zustand) | TR/EN i18n (`sf_locale`, default tr) |
| **Server state** | TanStack Query v5 — query key: `['users', params]` (liste), `['users', id]` (detay), `['users', id, 'effective-permissions']`; mutation'lar collection prefix'i invalidate eder |

**Form/validation/loading/error pattern:**
- Validation: backend Bean Validation → `fields[]` → `extractFieldErrors(err)` → inline form hataları
- Global mutation error: `QueryClient.mutations.onError` → `notifyApiError` (toast; field-level ve 401 hariç)
- Loading: `isLoading` (bootstrap) / `isFetching` (liste); `Spinner` tek animate-spin kaynağı
- 401 redirect: refresh başarısız → `sessionExpiredHandler` → `RequireAuth` → `/login`

### 2.6 UI/Component Mimarisi

- **Folder-by-feature:** `features/<domain>/` (pages + api.ts + hooks.ts + types.ts + components/)
- **Data-driven routing:** `app/Routes.ts` → `SHELL_ROUTES[]` (path/Component/authority) → App `<Route>` + `RequirePermission` map'ler; lazy chunk'lar
- **Navigation:** `app/Navigation.ts` → `NAV_ITEMS` + `NAV_GROUPS` (authority-filtered; boş grup gizli)
- **Design system:** `components/ui/` — DataTable (sortable header + toolbar slot + pagination footer), Modal, Button, Badge, SelectInput (react-select tek select), RowMenu, Field, Toggle, CheckboxList, SearchInput, Spinner, EmptyState, ConfirmDialog, TextArea
- **Page scaffold:** `Page.tsx` (head: title/description/actions + body; breadcrumb AppShell topbar'a portal)
- **Detail pattern:** `components/detail/` — DetailPanel/DetailField/AssignSection/PermissionListModal
- **Styling:** Tailwind CSS v4 `@theme` token'ları (`src/index.css`), light-only; z-index skalası (0/20/50/60); spacing skalası dokümante
- **i18n:** Homegrown zero-dependency (`lib/i18n/`) — flat key, TR+EN dictionary, `MessageKey` type compile-check
- **Permission mirror:** `lib/permissions.ts` — backend `PermissionCatalog`'ün tek frontend kopyası

### 2.7 Veri CRUD Akışı

- **Create:** Controller (`@Valid` DTO) → Service (`@Transactional`) → existsBy check (defense-in-depth) → entity persist → audit `record()` → response DTO
- **Read:** Service (`@Transactional(readOnly=true)`) → Specification-driven filter engine / EntityGraph (N+1 kapalı) → DTO map
- **Update:** Guard'lar (LastAdminGuard vb.) → mutasyon → session revoke (yetki değiştiyse) → audit (delta ile) → response
- **Delete:** Guard'lar (self-delete yasak, last-admin) → soft-delete (`@SQLDelete` UPDATE) → revoke → audit
- **Uniqueness race:** DB partial unique index (`WHERE is_deleted = false`) → `DataIntegrityViolationException` handler → 400 `*_TAKEN`

### 2.8 Backend Katman Sorumlulukları

Proje klasik DDD katmanlarına ayrılmamış; **pragmatik Spring katmanlaması** kullanıyor:

| Katman | Sorumluluk |
|--------|-----------|
| `controller/` | HTTP → DTO map, `@PreAuthorize`, validation tetikleme; iş mantığı YOK |
| `service/` | İş mantığı, transaction边界, guard'lar, revoke, audit çağrıları — side effect'lerin tek kaynağı |
| `security/` | Filter'lar, token provider, authority resolution, revoke service, rate limit |
| `config/` | JPA/security/CORS konfig + ApplicationRunner'lar (TenantMigration, RbacSeeder, PlanSync, ModuleSync, SystemAdminBootstrap) |
| `persistence/entity/` | JPA entity'ler (hiyerarşi: AuditEntity → SoftDeleteAuditEntity → BaseEntity / GeneratedIdAuditEntity) |
| `persistence/repository/` | Spring Data repo'lar + çok sayıda proje-özel projection query |
| `common/` | `TenantContext` + shared exception (Spring/JPA YOK) |

### 2.9 Veritabanı & Migration

```
db/migration/
├ public/   # startup auto-config — public şema
│   V1__tenant_registry.sql, V1.1__signup_verification_tokens.sql, V2__plans_subscriptions_modules.sql
├ tenant/   # programmatik — her tenant şemasında (provisioning'de + TenantMigrationRunner mevcut tenant'lara)
│   V1__iam_users.sql, V1.1__iam_rbac.sql, V1.2__audit.sql, V1.3__pm_projects_tasks.sql
└ module/   # modül-başı (ownMigrations=true), aktivasyonda — core tenant/ ağacının DIŞINDA
    └ apps/V1__app_builder.sql  (jsonb kolonlar, GIN jsonb_path_ops, per-module history flyway_schema_history_mod_apps)
```

- **K-36 squash:** pre-1.0.0 migration'lar `V1.x` baseline ailesine indirildi; yeni migration'lar `V2+`
- **Test profili:** H2 (`MODE=PostgreSQL`) + `flyway.enabled=false` + `create-drop` — build Docker gerektirmez; JSONB/search_path H2'de koşmaz → gerçek PG testleri gated IT'ler (`-Dforgesys.pg.it=true`, Testcontainers)
- **Entity hiyerarşi:** UUID PK, `t_` prefix, soft-delete (`@SQLRestriction` + `@SQLDelete`), optimistic locking (`version`), auditing (created/updated + by, `timestamptz`)

### 2.10 Observability & Audit

- **3 katman log (K-19 core DONE):** `t_audit_logs` (admin aksiyon, append-only trigger + delta JSON) + `t_login_history` (her login denemesi, append-only) + MDC traceId (`X-Request-Id`/UUID)
- **Audit yazımları:** `REQUIRES_NEW` + best-effort (iş sürecini asla bozmaz)
- **Read side:** `GET /audit-logs` + `GET /login-history` (`iam:audit:read`, sayfalı + filtre + `q`)
- **Eksik:** request/trace log tablosu (`GET /request-logs`), Prometheus metrics, OpenTelemetry

### 2.11 Test Stratejisi

| Seviye | Durum |
|--------|-------|
| Unit (service/validator/guard) | ✅ ~100+ test (H2 profile) |
| Controller (MockMvc + gerçek security filter) | ✅ 20+ test sınıfı (401/403→happy→404 pattern) |
| Cross-tenant isolation | ✅ `CrossTenantIsolationTest` (gated Testcontainers gerçek PG) |
| Module activation IT | ✅ `ModuleActivationIT` (gated gerçek PG) |
| App Builder IT | ✅ `AppBuilderIT` (gated gerçek PG — JSONB search, iki-tenant izolasyon) |
| Redis IT | ✅ `RedisRefreshTokenIT` (gated) |
| **Frontend test** | ❌ **YOK** (Vitest/RTL planlı, hiç kurulmadı) |
| E2E | ❌ YOK |

### 2.12 Build / CI / Deployment

- **Build:** `mvn clean install` (testler H2 — Docker'sız); frontend Maven build'de backend jar'a gömülür (frontend-maven-plugin); `npm run lint` (oxlint) + `tsc -b`
- **CI:** `.github/workflows/ci.yml` — PR'da backend build + frontend lint/build
- **CD:** YOK (Docker build/push/deploy manuel — `docker-compose-prod.yml`)
- **Profil:** dev (default, localhost PG/Redis gömülü default) / prod (`.env` credential) / test (H2)

---

## 3. Mevcut mimari neden bu şekilde?

Her önemli karar `docs/DECISIONS.md`'de K-XX/RISK-XX/DEBT-XX olarak belgelenmiş. Kritik olanlar:

| Karar | Gerekçe | Referans |
|-------|---------|----------|
| **Schema-per-tenant (SCHEMA strategy)** | DISCRIMINATOR: tek `WHERE tenant_id` unutulursa tüm kiracılar sızar — kabul edilemez. DATABASE: per-tenant pool/migration/backup operasyonel ağır. SCHEMA: strong isolation + tek pool + tenant bazlı backup | ARCHITECTURE.md |
| **İki fazlı signup (K-21)** | Open endpoint'te ağır DDL + subdomain squatting riski → faz 1 hafif (PROVISIONING + token), faz 2 kullanıcının link tıklamasıyla senkron provision | K-21 |
| **Peppered BCrypt (K-23)** | DB leak tek başına hash kıramaz (pepper DB dışında); per-tenant pepper ek güvenlik getirmez, key yönetim riski ekler → global pepper | K-23 |
| **Refresh Redis-first (K-34)** | Revocability; tablo dead code churn; rotasyon + reuse detection atomik Lua; hash-at-rest (RISK-30 felsefesi) | K-34 |
| **Registry'ler kodda (DB katalog tablosu YOK)** | Modül/plan = kod (entity/service/migration); kayıt kodla gitmeli, DB'den sapmamalı | K-16 |
| **Modül-başı Flyway history** | Flyway location scan RECURSIVE — modül ağacı `tenant/` içinde olsaydı core history'ye karışırdı; `flyway_schema_history_mod_<key>` ile bağımsız versiyonlama | K-16 |
| **Transaction split (FK-deadlock önleme)** | Activation kaydı caller tx'ine katılır (provisioning outer tx'teki commit edilmemiş Company FK lock'u); sadece permission seed `REQUIRES_NEW` (tenant şema yazısı, outer session public'e pinned — RISK-26) | K-16 |
| **All-permissions flag (K-35)** | Runtime permission eklendiğinde Admin'e explicit grant defter tutmayı ve silme UX'ini bozmadan implicit süper-kullanıcı semantiği | K-35 |
| **Privilege-change session revoke** | Yetkiler JWT'ye issue anında gömülü → rol/izin değişimi token TTL'ine kadar geçerli kalırdı (privilege-retention penceresi) → `tokenInvalidBefore` bulk stamp + refresh drop ile anında etkili | Faz 1 IAM |
| **LastAdminGuard (RISK-35)** | Son aktif admin kaybedilirse tenant kilitlenir — tarihsel olarak yaşanmış; 11 write path'e wired | RISK-35 |
| **UserDirectoryView (@Subselect)** | Liste N+1'siz; join + count DB'de derived table; H2+PG portable; migration yok | user directory fazı |
| **JSONB düz String mapping** | hypersistence-utils dependency'siz (AuditLog convention); `stringtype=unspecified` PG bind | K-15 |
| **Structured view/filter DSL** | Serbest expression dili yok → injection yüzeyi yapısal olarak kapalı; record search ile paylaşılan validator | K-15 spike |
| **K-36 migration squash** | Pre-1.0.0 penceresi (deploy edilmemiş DB yok) → V1..V8 → V1.x baseline ailesi; yeni migration V2+ | K-36 |

---

## 4. Hangi kısımlar iyi ve korunmalı?

| Alan | Neden iyi | Seviye |
|------|-----------|--------|
| Schema-per-tenant isolation + `SET search_path` + regex validation | Güvenlik kritik; en kritik bug sınıfı tenant leak | 🔒 CRITICAL |
| TenantFilter (-101) → TenantContext → Resolver zinciri + finally clear | Tek sorumluluk, merkezi, test edilmiş | 🔒 CRITICAL |
| JWT tenant claim binding (RISK-19) | Cross-tenant escalation'ın tek engeli | 🔒 CRITICAL |
| İki katmanlı revoke (`tokenInvalidBefore` + `jti` blacklist) | User-scoped + granular birlikte | ✅ KEEP |
| Refresh rotasyon + reuse detection (atomik Lua) | Token çalınma/yarış senaryoları kapalı | ✅ KEEP |
| PepperingPasswordEncoder + lazy rehash | Modern OWASP önerisi uygulandı | ✅ KEEP |
| `@PreAuthorize` + DB-driven authority resolution (lazy traversal YOK) | N+1'siz, cycle-safe (visited set), explicit | ✅ KEEP |
| Rol kalıtımı + `all_permissions` flag | Esnek + runtime permission'a tepkili | ✅ KEEP |
| LastAdminGuard | Operasyonel kilitlenme önlenir | ✅ KEEP |
| Module system (enum registry + per-module Flyway + transaction split) | Production-ready, idempotent, deadlock-safe | ✅ KEEP |
| Custom App Builder (JSONB EAV + native PG search + structured DSL) | Injection-safe, GIN-backed, plan soft-block | ✅ KEEP |
| `PageResponse` / `ApiErrorResponse` / `ErrorCode` (stable wire codes) | API sözleşme bizim, framework'ün değil | ✅ KEEP |
| Sort/filter whitelist (metamodel sabitleriyle) | Rename build'i kırar — güvenli by construction | ✅ KEEP |
| Append-only audit (DB trigger) + delta kaydı | Tamper-proof | ✅ KEEP |
| Frontend: data-driven routing + permission-gated nav + RequirePermission | Backend yetkisi UI'da aynen yansır | ✅ KEEP |
| TanStack Query + server-side sort/filter/search + query key disiplini | Client state minimal | ✅ KEEP |
| Cookie auth + transparent refresh (coalesced) | XSS-safe token + seamless UX | ✅ KEEP |
| Design system (ui/ primitives + spacing/z-index/i18n kuralları) | Tutarlı, dokümante | ✅ KEEP |
| Gated gerçek-PG IT'ler (Testcontainers) + Docker'sız H2 default build | DX + güvenlik doğrulaması dengesi | ✅ KEEP |
| İyi yazılmış AGENTS.md ekosistemi (kök + modül) | AI-assisted development için örnek düzeyde | ✅ KEEP |

---

## 5. Hangi kısımlar gereksiz veya aşırı karmaşık?

**Değerlendirme formatı:** Mevcut yaklaşım → Neden var → Gerçekten gerekli mi → Alternatif → Öneri → Risk/trade-off

### 5.1 MapStruct kararsızlığı
- **Mevcut:** Epic 2.1'de MapStruct planlandı, hiç uygulanmadı; kod tabanı manuel `toResponse` convention'ında; `AuthController.registerCompany()` hâlâ `Map<String,Object>` döndürüyor (dokümante tek istisna)
- **Neden:** Karar ertelendi
- **Gerekli mi:** Hayır — MapStruct'e gerek yok (manuel convention tutarlı çalışıyor); ama `Map` dönüşü düzeltilmeli
- **Öneri:** MapStruct **iptal** (Roadmap'te Epic 3.0.C'de zaten iptal edilmiş — tutarlı biçimde kök dokümanlara işle); `AuthController` DTO döndürsün

### 5.2 Explicit `auditService.record()` çağrıları (AOP yok)
- **Mevcut:** Her write metotta manuel çağrı; unutulma riski
- **Neden:** K-27'de `@AuditLog` AOP planlı; AOP infra classpath'te ama uygulanmadı
- **Gerekli mi:** Kısmen — explicit çağrılar traceable ama coverage garantisi yok
- **Öneri:** Refactor — `@AuditLog` aspect (K-27 kapsamında; high-risk body capture ile birlikte). Trade-off: AOP implicit (debug zor) ama unutulamaz

### 5.3 `t_sessions_log` belirsizliği
- **Mevcut:** K-28'de planlandı, "ertelendi — `t_login_history`/`t_audit_logs` ile örtüşme" kararı alındı; tablo hiç yaratılmadı (temiz)
- **Öneri:** Remove — planın tamamen iptali olarak DECISIONS.md'ye işle

### 5.4 InMemory/Redis store parity bozuklukları (bilinen, RISK-36'da listeli)
- `InMemoryRefreshTokenStore.revoke` rotasyon zincirini (`rotatedTo`) takip etmiyor (test-parite bozuk)
- Redis kesintisinde fail-closed davranış (rate-limit/blacklist "fail-open" yorumu fiilen exception → 500)
- Revoke zincir yürüyüşü Redis'te de atomik değil
- **Öneri:** Fix — InMemory zincir takibi eklenmeli (test güvenilirliği); Redis fail davranışı bilinçli karar olarak belgelenmeli

### 5.5 Rate limit test profilinde disabled
- **Mevcut:** `InMemoryRateLimiter` test'te etkin değil → rate limit davranışı H2 testlerinde test edilemiiyor
- **Öneri:** Fix — testte etkinleştir (property-driven), IT ile gerçek Redis zaten gated doğrulanıyor

### 5.6 `X-Tenant-ID` dev fallback + `tenantStore` localStorage
- **Mevcut:** Dev profilde header fallback; frontend `sf_tenant_id` localStorage — prod'da subdomain esas
- **Neden:** localhost:3000'de subdomain çözümlemesi çalışmaz (dev UX)
- **Gerekli mi:** Evet (dev), ama iki tenant çözümleme yolu kavramsal karmaşa yaratıyor
- **Öneri:** Keep (dev-only, dokümante) — ama `*.localhost` kullanımı teşvik edilerek tek yol'a evrilmeli

### 5.7 `User` entity'de kullanılmayan token field'ları
- **Mevcut:** `emailVerificationToken`/`passwordResetToken` + expiry alanları hazır ama akış yok (Epic 2.9 ertelenmiş)
- **Öneri:** Keep + akışı bitir (MEDIUM) — alan silmek yerine flow implement etmek roadmap'te

### 5.8 Aynı problemi çözen çift yaklaşım kalıntıları
- `AuthController` Map dönüşü (yukarıda)
- Constraint name → ErrorCode manuel substring map — yeni constraint eklendikçe elle büyüyor (unutma riski)
- **Öneri:** Map'i tek yere toplayıp yorumla işaretleme konvansiyonu (migration dosyası ↔ map senkron); otomatik üretim over-engineering

### 5.9 Over-engineering DEĞİL (yanlış sanılan doğru karmaşıklıklar)
- Transaction split (FK-deadlock) — gerçek PG'de kanıtlanmış sorun
- Per-module Flyway history — recursive scan tuzağı IT'de keşfedildi
- `tokenInvalidBefore` saniyeye floor — hızlı re-login korunuyor
- Append-only trigger — admin compromise senaryosu
- Bunlar **belgeli gerçek problemlerin** çözümleri — sadeleştirme adayı DEĞİL

---

## 6. Neleri sadeleştirmeliyiz?

| Öncelik | Alan | Aksiyon | Trade-off |
|---------|------|---------|-----------|
| HIGH | `AuthController` Map dönüşü | `Map<String,Object>` → proper DTO (mevcut manuel convention) | Küçük wire değişikliği — pre-1.0 penceresinde serbest |
| HIGH | MapStruct planı | İptal olarak tüm dokümanlara işle (Roadmap zaten iptal etti) | Belge tutarlılığı |
| MEDIUM | InMemory store zincir takibi | `revoke` rotatedTo zincirini izlesin | Test parity artar |
| MEDIUM | Rate limit test enable | Property ile testte aç | Test coverage |
| MEDIUM | Constraint map merkezlendirme | Tek konum + konvansiyon yorumu | Bakım kolaylaşır |
| LOW | `t_sessions_log` planı iptali | DECISIONS.md'de kapat | Plan netliği |

---

## 7. Neleri değiştirmemeliyiz?

1. **Schema-per-tenant + `SET search_path`** — isolation'ın kalbi
2. **Filter order zinciri** (-102 → -101 → -100) — tenant, security'den önce
3. **JWT tenant binding (RISK-19)** — cross-tenant escalation engeli
4. **İki katmanlı revoke mimarisi**
5. **Enum registry'ler (PlanDefinition/ModuleDefinition/PermissionCatalog)** — DB katalog tablosu YOK
6. **Modül-başı Flyway history + transaction split pattern'i**
7. **`all_permissions` flag dynamic resolution**
8. **LastAdminGuard (11 write path)**
9. **UserDirectoryView read model (@Subselect)**
10. **K-36 squash sonrası V1.x baseline + V2+ sürümleme**
11. **Cookie-based auth + transparent refresh frontend pattern'i**
12. **Data-driven routing/navigation + permission mirror**
13. **H2 default build + gated gerçek-PG/Redis IT stratejisi**
14. **Soft-delete partial unique index pattern'i (RISK-17)**

---

## 8. 2026 standartlarına göre eksikler

| Kategori | Eksiklik | Öncelik |
|----------|----------|---------|
| Frontend testing | Vitest + RTL (hiç test yok) + MSW | **CRITICAL** |
| API dokümantasyonu | springdoc-openapi (Swagger UI) — dependency bile yok | **HIGH** |
| CI/CD | CD yok (Docker build/push/deploy manuel); CI PR-gate var ama frontend test yok | **HIGH** |
| Observability | Prometheus/Micrometer metrics; OpenTelemetry tracing; request/trace log tablosu | HIGH |
| User lifecycle | Tenant içi email verification + password reset akışı (field'lar hazır) | MEDIUM |
| Secrets | Pepper rotasyonu desteklenmiyor (belgeli); rotation runbook yok | MEDIUM |
| Security policy | Password complexity policy (ürün kararı bekliyor) | LOW |
| Notification | K-29 (in-app + mail) — planlı, bekliyor | MEDIUM |
| Approval workflow | K-27 `@ApprovalRequired` + `t_pending_actions` | LOW |
| Anomaly detection | K-27 passive alert | LOW |
| Activity feed | K-30 | LOW |
| E2E test | Playwright — critical path'ler | MEDIUM |
| Gateway/TLS | K-33 Nginx + wildcard DNS-01 — %90 sonrası erteli (bilinçli) | LOW (Faz 5) |
| Billing | Faz 6 — planlı | LOW (Faz 6) |

**Modernlik değerlendirmesi (dengeli):** Java 21 / Spring Boot 4.1 / Jackson 3 / React 19 / TS 6 / Tailwind v4 / TanStack Query v5 / Zustand 5 / oxlint — stack 2026-uyumlu. Eksik olan teknoloji değil; **quality gates** (frontend test), **dokümantasyon** (OpenAPI) ve **operasyonel olgunluk** (CD, metrics).

---

## 9. Yeni mimari / yaklaşım nasıl olmalı?

### 9.1 Mimari prensipler (projede somut karşılıklarıyla)

| Prensip | ForgeSys'te karşılığı |
|---------|----------------------|
| Single Source of Truth = Code | Registry'ler enum'da (`ModuleDefinition`, `PlanDefinition`, `PermissionCatalog.CORE`); permission mirror `lib/permissions.ts` |
| Tenant isolation non-negotiable | `TenantFilter` tek geçiş; controller'da tenant validate YOK; her query tenant şemasına düşer |
| Explicit > Implicit | Audit explicit çağrılar (AOP'ye geçiş K-27 kararında); config-driven behavior; stable error codes |
| Database-driven resolution | Authority'ler lazy collection yerine projection query'lerle |
| Stateless access + stateful revoke | JWT gömülü authority + `tokenInvalidBefore`/jti blacklist |
| Soft-block limits | Plan limiti create'te 403; mevcut veri asla gizlenmez/silinmez |
| Idempotent operations | Activation/provisioning/sync — retry-safe |
| Explicit transaction boundaries | `REQUIRES_NEW` yalnız tenant-schema yazısında; FK-deadlock pattern'i |
| Frontend = backend mirror | Permission constants, PageResponse/ApiErrorResponse normalize, whitelist sort key'ler |
| Test parity | H2 default (DX) + gated gerçek PG/Redis (güvenlik doğrulaması) |
| Secure-by-default | Pepper fail-fast, CSRF-aware cookie auth, CSP/HSTS header'ları, append-only audit |
| Observable system | MDC traceId her error'da; 3 katman log |

### 9.2 Stack hizası

Mevcut stack korunur (section 8'deki gap'ler kapatılır); yeni teknoloji eklenmez — **modernlik = doğru problemi doğru seviyede çözmek**.

---

## 10. Bundan sonraki feature geliştirme süreci

### 10.1 Standart akış

```
Problem / Requirement
    ↓  ( kullanıcı ihtiyacı net mi? hangi kullanıcı senaryosu? )
Domain & Workflow Understanding
    ↓  ( hangi domain/modül? mevcut pattern'lerle çözülür mü? )
Technical Decision (ADR gerekliyse yaz: Bağlam → Karar → Sonuç → Trade-off)
    ↓  ( yeni migration? yeni module? yeni permission? TenantMigrationRunner etkilenir mi? )
Minimal Design (API contract → migration → service → controller → frontend types/hooks)
    ↓
Implementation Plan (task breakdown + test planı: unit/controller/IT)
    ↓
Implementation (test-first; her yeni endpoint için en az bir test — AGENTS.md kuralı)
    ↓
Validation (mvn test + npm lint/build + gated IT'ler tenant izolasyonu etkiliyorsa)
    ↓
Documentation (ADR → DECISIONS.md; endpoint → backend/AGENTS.md; mimari etki → ARCHITECTURE.md)
```

### 10.2 Aşama aşama verilen kararlar (Definition of Ready)

| Aşama | Verilen karar |
|-------|---------------|
| Domain | Feature hangi modüle ait? Yeni modülse `ModuleDefinition` kaydı + migration ağacı |
| Technical | Migration lokasyonu (public/tenant/module); mevcut tenantları etkiliyorsa `TenantMigrationRunner` otomatik — yeni dosya yeter |
| API Contract | DTO record'ları, `ErrorCode`'lar, permission'lar (`{module}:{resource}:{action}`), PageResponse uyumu |
| Security | `@PreAuthorize` mü / authenticated-only mı / public mı? Rate limit gerekli mi? |
| Frontend | `features/X/` yapısı, permission constant, route/nav kaydı, i18n key'ler (TR+EN) |
| **Karar dondurma** | Implementation başladıktan sonra API contract ve migration tasarımı değiştirilmez — yeni gereksinim = yeni karar |

### 10.3 Süreç disiplini (uzayan geliştirme sürecinin dersleri)

- **DoR/DoD zorunlu:** Feature başlamadan contract netleşir; bitmeden test + dokümantasyon tamamlanır
- **Erken abstraction YOK:** İlk concrete implementasyon; tekrar 2+ yerde görülünce abstraction (mapstruct/formula/activity-feed dersleri)
- **Kararlar ADR ile yaşar:** "ileride lazım olur" düşüncesiyle kod yazılmaz; DECISIONS.md'de Planlandı statüsüyle yaşar
- **Ask-first kuralları** (AGENTS.md): yeni migration / yeni dependency — dokunmadan önce
- **Dokümantasyon = development'ın parçası:** Kod-mimari-dokümantasyon kopukluğu en büyük süreç riski; her PR doküman delta'sı içerir

---

## 11. Standartlaştırılmış kararlar (tekrar tartışılmayacak)

| # | Karar | Referans |
|---|-------|----------|
| 1 | Multi-tenancy = schema-per-tenant | ARCHITECTURE.md |
| 2 | Registry'ler kodda (enum) — DB katalog tablosu yok | K-16 |
| 3 | Modül migration'ları `db/migration/module/<key>` + per-module history | K-16 |
| 4 | Auth = RS256 JWT cookie + opaque refresh (Redis, rotasyon + reuse detection) | K-34 |
| 5 | Revoke = `tokenInvalidBefore` (user-scoped) + `jti` blacklist (granular) | RISK-21 + K-34 |
| 6 | RBAC = `@PreAuthorize` + `{module}:{resource}:{action}` namespace | K-26 |
| 7 | Authority resolution = DB-driven (direct + active group + transitive parent) | CustomUserDetailsService |
| 8 | Admin = `all_permissions` flag (implicit) | K-35 |
| 9 | LastAdminGuard 11 write path'te | RISK-35 |
| 10 | Plan/module limitleri = soft-block 403, veri asla gizlenmez | K-15/K-16 |
| 11 | Wire contract = `PageResponse` + `ApiErrorResponse` + stable `ErrorCode` | Faz D |
| 12 | Sort/filter = whitelist + metamodel sabitleri | backend/AGENTS.md |
| 13 | Frontend = data-driven routing + RequirePermission + TanStack Query + Zustand | frontend/AGENTS.md |
| 14 | Auth transport = httpOnly cookie + transparent refresh | K-34 |
| 15 | Migration sürümleme = V1.x baseline + V2+ (K-36 sonrası) | K-36 |
| 16 | Test stratejisi = H2 default + gated gerçek PG/Redis IT | Faz B |
| 17 | JSONB = düz String + `columnDefinition="jsonb"` (hypersistence-utils yok) | K-15 |
| 18 | DTO mapping = manuel `toResponse` convention (MapStruct iptal) | Bu analiz |
| 19 | Şifre = Peppered BCrypt(12) | K-23 |
| 20 | Audit = explicit service çağrıları bugün; `@AuditLog` AOP K-27 kapsamında | K-27 |

---

## 12. Güncellenmesi gereken dokümanlar

> Yalnızca bu analizde mutabık kalınan değişiklikler doğrultusunda güncellenmeli (kapsamı şişirmeden).

| Doküman | Güncelleme | Öncelik |
|---------|-----------|---------|
| `AGENTS.md` (kök) | MapStruct iptal kararı; frontend test stratejisi; frozen decisions listesi (section 11) | HIGH |
| `backend/AGENTS.md` | `AuthController` Map istisnası kapanınca satırı kaldır; springdoc endpoint tablosu | HIGH |
| `frontend/AGENTS.md` | Test bölümü ("none yet" → Vitest/RTL setup + pattern) | HIGH |
| `docs/DECISIONS.md` | Yeni kayıtlar: MapStruct iptali, frontend testing kararı, CI/CD kararı (uygulandıkça) | HIGH |
| `docs/ARCHITECTURE.md` | Observability bölümü (metrics/trace) eklendikçe; springdoc | MEDIUM |
| `docs/ROADMAP.md` | Faz 5 CI/CD + observability kalemleri netleşti; Epic 2.1 (MapStruct) iptal notu kök seviyede | MEDIUM |
| `README.md` | Frontend test komutları; Swagger URL (springdoc gelince) | MEDIUM |

---

## 13. Uygulama öncesi tamamlanması gereken kararlar

| # | Karar | Açıklama | Ne zaman |
|---|-------|----------|----------|
| 1 | Frontend test framework kurulumu | Vitest + RTL (+MSW?) — paket seçimi, config, ilk test (auth flow/login page), CI'e ekleme | Implementasyon session'ı başlamadan |
| 2 | Springdoc-openapi | Dependency (root pom onayı — ask-first kuralı), profile gating (dev'de açık, prod'da kapalı mı?) | Aynı |
| 3 | `AuthController` DTO düzeltmesi | `Map<String,Object>` → record; wire değişikliği pre-1.0'da serbest | Aynı |
| 4 | InMemory store parity fix | `revoke` zincir takibi | Aynı |
| 5 | Rate limit test enable | Property ile | Aynı |
| 6 | CI/CD scope | Mevcut CI'e frontend test adımı; CD (Docker push/deploy) Faz 5'te mi şimdi mi? | Karar (uygulama sonra) |
| 7 | Password complexity policy | Ürün kararı — uygulanırsa tüm test/bootstrap şifreleri değişir | Ürün kararı bekliyor |
| 8 | Email verification / password reset flow | Mail infra (Faz 5 SMTP) bağımlılığı var; şema hazır | Faz 5 ile birlikte |

---

## 14. Önceliklendirilmiş aksiyon planı

### Critical (hemen)
- [ ] Frontend test altyapısı (Vitest + RTL) + ilk kritik path testleri + CI'e ekleme
- [ ] Springdoc-openapi kurulumu (ask-first: dependency onayı)
- [ ] `AuthController` Map → DTO + MapStruct planının resmi iptali

### High (1-2 sprint)
- [ ] InMemory/Redis store parity (zincir takibi + rate limit test enable)
- [ ] CI pipeline'a frontend test adımı
- [ ] Dokümantasyon senkronizasyonu (section 12 — yüksek öncelikliler)

### Medium (2-4 sprint)
- [ ] Observability: Prometheus/Micrometer metrics expose
- [ ] Request/trace log tablosu + `GET /request-logs` (K-19 tamamlama)
- [ ] `@AuditLog` AOP + high-risk body capture (K-27)
- [ ] Notification subsystem (K-29 — mail infra Faz 5 bağımlı)
- [ ] Email verification + password reset flow (Faz 5 SMTP ile)

### Low / Deferred (bilinçli ertelenmiş — roadmap'te yaşıyor)
- [ ] Approval workflow (K-27), anomaly detection, activity feed (K-30)
- [ ] Nginx gateway + TLS (K-33 — %90 sonrası)
- [ ] Billing (Faz 6)
- [ ] Password complexity (ürün kararı)
- [ ] E2E testler (frontend test olgunlaşınca)

---

## Sonuç

ForgeSys **mimari olarak sağlam bir temel** üzerine kurulu, iyi dokümante edilmiş bir platformdur. Kod tabanındaki karmaşıklıkların büyük bölümü **belgeli gerçek problemlerin** (FK-deadlock, recursive scan, privilege-retention, tenant escalation) çözümleridir ve korunmalıdır.

Gerçek riskler teknik mimaride değil; **süreç ve kalite kapılarındadır**: frontend testi yokluğu, API dokümantasyonu yokluğu, CD yokluğu ve ask-first kültürünün sürdürülmesi. Bu analiz; hangi kararların donmuş olduğunu, hangi eksiklerin hangi öncelikle kapatılacağını ve bundan sonraki her feature'ın hangi akıştan geçeceğini netleştirir.

> Bu doküman planlama session'ının (2026-08-22) anlık görüntüsüdür. Uygulama ilerledikçe section 14 işaretlenmeli ve ilgili kararlar DECISIONS.md'ye ADR olarak taşınmalıdır. Çelişki durumunda DECISIONS.md (karar kayıtları) ve modül AGENTS.md'leri esas alınır.
