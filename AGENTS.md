# AGENTS.md

## Project

**ForgeSys** — modular multi-tenant SaaS platform. Java 21 + Spring Boot 4.1, PostgreSQL 16, Redis 7.4, Flyway. Hybrid model: built-in modules (Tasks/Notes/Warehouse/Logistics — Odoo/ERPNext style) + tenant custom apps (Notion/Airtable style, JSONB EAV). **Schema-per-tenant** isolation; **user-per-tenant** (no global users); RBAC (User-Role + Group-Role + Role-Permission).

## Language Policy (token optimization)

- **Reasoning / chain-of-thought:** English.
- **AI-facing docs (all `AGENTS.md` files):** English.
- **User-facing docs (`README.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/DECISIONS.md`):** English/Turkish mix is allowed; prefer Turkish where English obscures meaning.
- **User communication (questions, answers, explanations, summaries):** Turkish.
- **Code, commit messages, file/folder names, technical terms:** English.
- **Only English and Turkish** are permitted — no other languages.

## Documentation map

- [`README.md`](README.md) — setup, running, **build commands** (single source), API, troubleshooting. (TR/mixed)
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture diagram, request lifecycle, schema-per-tenant, entity hierarchy, **config profiles** (single source). (TR/mixed)
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — phase/epic roadmap (no ticket numbers, goal-oriented). (TR/mixed)
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — decision log (K-XX architecture, RISK-XX risk, DEBT-XX tech debt). (TR/mixed)
- [`docs/FULL_ANALYSIS.md`](docs/FULL_ANALYSIS.md) + [`docs/ANALYSIS_ADDENDUM.md`](docs/ANALYSIS_ADDENDUM.md) — 2026-08-22 planning session: system analysis, simplification plan, frozen decisions (§11 + addendum §8), prioritized action plan. (TR)
- Each module has its own `AGENTS.md`: [`common/`](common/AGENTS.md) · [`persistence/`](persistence/AGENTS.md) · [`backend/`](backend/AGENTS.md) · [`frontend/`](frontend/AGENTS.md). (all EN)

## Setup (summary)

Full detail and all commands live in `README.md`. Summary:

```bash
mvn clean install          # all modules (tests run on H2, no Docker required)
docker compose up -d       # db + redis (dev infra)
# backend: run/debug ForgeSysApplication from the IDE (dev profile)
# frontend: cd frontend && npm install --include=optional && npm run dev
```

- `.env` is for prod Docker Compose only; not needed in the `dev` profile. Never committed (in `.gitignore`).

## Modules

Each module has its own `AGENTS.md` with module-specific rules.

- `common/` — shared core (`TenantContext`, shared exceptions). **NO Spring/JPA.**
- `persistence/` — JPA entities + multi-tenancy infrastructure + Flyway migration.
- `backend/` — Spring Boot application (controller/service/security/config). Produces the executable jar.
- `frontend/` — React 19 + TypeScript + Vite SPA.

## Operational infrastructure (`infra/`)

Not source code; runtime/operational files. Details in `infra/README.md`.

- `infra/config/` — prod externalized override. Dropping an `application-prod.yaml` here overrides the one inside the jar (`SPRING_CONFIG_ADDITIONAL_LOCATION`). **Do not commit secrets.**
- `infra/data/` — bind-mount volumes (`postgres/` dev+prod; `redis/` prod only — dev redis uses a named volume). **Not committed.** Ownership is auto-fixed by the one-shot `data-init` compose service (postgres UID 70, redis UID 999); a wiped `infra/data` is recoverable with `docker compose up -d --force-recreate db`.
- `infra/init-sql/` — Docker postgres `/docker-entrypoint-initdb.d/` scripts. Run **only on first DB creation** (extension, role). **Completely separate from Flyway migrations** — do not mix.
- `infra/logs/` — Spring Boot file appender + container log bind-mount. **Not committed.**
- `infra/ssl/` — TLS certificates / private keys. **NEVER commit** (conflicts with the "Limits / Never" rule below).
- `infra/templates/` — externalized runtime templates (mail HTML/CSS etc.).

**init-sql vs Flyway (critical distinction):** Flyway runs every startup from `flyway_schema_history` (versioned). `init-sql/` is run by the postgres image **only when the data directory is empty** (first install). Never put the same file in both — Flyway checksum/history consistency breaks.

## Critical rules (all modules)

- **Tenant isolation is MANDATORY.** No query may skip the tenant filter. Tenant data leakage is the most critical bug class. The tenant context is established by `TenantFilter` (`common.TenantContext` ThreadLocal); do NOT validate tenant in the controller.
- **The root pom is only a lightweight parent + aggregator** — it does not impose dependencies on modules (no `<dependencies>`), only version management. No module uses `spring-boot-starter-parent` as parent.
- **Cyclic dependencies between modules are FORBIDDEN.** Dependency graph: `common` <- `persistence` <- `backend`. `frontend` is independent.
- **Versions live in the root `<properties>`** (`spring-boot.version`, `java.version`). Module poms do not pin versions.
- **IDs are UUID everywhere** (`GenerationType.UUID`). Table names use the `t_` prefix.
- **Code style:** package `com.ibrhalil.forgesys.*`, DTOs are `record`, centralized error handling via `@RestControllerAdvice` (`ApiErrorResponse` + `ErrorCode`), Lombok in the backend module.

## Engineering principles

General engineering conduct. Project-specific rules above take precedence; the system prompt covers comments, output brevity, and conventions.

- **Investigate before implementing.** Search for existing implementations, reusable components, and conventions first. Prefer improving existing code over introducing new code. State assumptions explicitly when requirements are ambiguous.
- **No unrequested features.** Solve exactly the requested problem — no gold plating, no speculative abstractions (interfaces/factories/builders/generics) that do not solve a real problem today.
- **Query performance.** Check for N+1 before proposing ORM solutions; favor `@EntityGraph`/`JOIN FETCH` for lazy associations. Multi-tenant queries multiply cost — every query crosses a tenant schema.
- **Thread safety.** `TenantContext` is a `ThreadLocal` — it does NOT propagate across `@Async`/executor threads without a `TaskDecorator` ([RISK-10](docs/DECISIONS.md#risk-10)). Always `clear()` in `finally`.
- **Backward compatibility.** Do not break endpoint contracts (`/api/v1/*`) without explicit intent. Deprecate before removing; version when behavior changes.

## Refactor Roadmap (2026-07-24 review)

Kapsamlı 4-katmanlı review (service/security/persistence/test) + Spring Boot 4.0 / Security 7 resmi migration kaynakları. Bulgular önem sırasına göre fazlara bölünmüş. Detay ve dosya:ref'ler: [`docs/DECISIONS.md`](docs/DECISIONS.md) RISK-19..RISK-34. Tüm fazlar uygulanacak (kullanıcı kararı), önem sırasıyla.

### Faz 3.0.A — Module System & Plan/Subscription (K-16) — UYGULANDI (2026-08-22)

Detay + uygulama kararları: [`docs/DECISIONS.md K-16`](docs/DECISIONS.md#k-16) · modül bazlı kurallar: [`persistence/AGENTS.md`](persistence/AGENTS.md) + [`backend/AGENTS.md`](backend/AGENTS.md).

- Registry kodda (DB katalog tablosu YOK): `PlanDefinition` (FREE/PRO/ENTERPRISE, `PlanSyncRunner` `t_plans` upsert) + `ModuleDefinition` (key/displayName/minPlan/flywayLocation/permissions; `pm` modüle çevrildi — permission'ları modül sahipliğine taşındı).
- `public/V2` migration: `t_plans`/`t_subscriptions`/`t_tenant_modules`. Modül migration'ları `db/migration/module/<key>` + `flyway_schema_history_mod_<key>` (modül-başı bağımsız versiyonlama; core `tenant/` ağacı DIŞINDA — recursive scan tuzağı).
- `ModuleActivationService`: plan gate → modül Flyway → permission seed (`REQUIRES_NEW`) → aktivasyon kaydı (caller tx — provisioning FK deadlock önleme). `ModuleSyncRunner`: FREE backfill + default modüller + re-sync.
- Endpoint'ler: `GET /api/v1/modules` (`iam:module:read`) + `POST /modules/{key}/activate` (`iam:module:write`, idempotent).
- Testler: 398 H2 + `ModuleActivationIT` (gated gerçek PG — provisioning hook, permission seed, modül history izolasyonu).

### Faz 3.0.B — Custom App Builder Backend (K-15) — UYGULANDI (2026-08-22)

Detay + uygulama kararları: [`docs/DECISIONS.md K-15`](docs/DECISIONS.md#k-15).

- `apps` modülü (`ModuleDefinition.APPS`, `ownMigrations=true`, FREE + default — `default-keys: pm,apps`): `db/migration/module/apps/V1__app_builder.sql` → `t_apps`/`t_app_properties(config jsonb)`/`t_app_records`/`t_app_record_values(value jsonb, GIN jsonb_path_ops, backtick-quoted kolon — H2 reserved)`/`t_app_views(config jsonb)`. Value satırları soft-delete'siz (`GeneratedIdAuditEntity`) — clear = satır silinir.
- JSONB mapping düz `String` + `columnDefinition="jsonb"` (AuditLog emsali; hypersistence-utils YOK) + `stringtype=unspecified`. Entity'ler: `App`/`AppProperty`/`AppRecord`/`AppRecordValue`/`AppView` + `PropertyType`/`ViewType` enum'ları.
- Plan limitleri `PlanDefinition`'da (maxApps/maxRecordsPerApp: 3/1k · 25/50k · -1/-1) — soft-block `PlanLimitService` (403 `app_limit_reached`). Test-profile fallback default-keys bilinçli `pm` kalır.
- Service'ler: `AppBuilderService` (app/property/view CRUD; FORMULA reddedilir, SELECT/RELATION config doğrulama, property tipi immutable) + `AppRecordService` (record CRUD, PATCH partial-merge — JSON null clear, required korunur; bulk value fetch N+1'siz) + `AppPropertyValueValidator` (USER/RELATION tenant içi varlık kontrolü) + `AppQueryValidator`/`AppViewConfigValidator` (structured filter/sort DSL — injection yüzeyi yok; BOARD groupBy/CALENDAR dateProperty zorunlu).
- `AppRecordSearchExecutor` — native PG JSONB search (`@>` EQ, `#>>` CONTAINS/DATE, `::numeric` NUMBER, GIN-backed; explicit `?N` parametreler, enum-fragment SQL). `POST /apps/{id}/records/search` PG-only (H2'de koşmaz).
- Endpoint'ler: `/api/v1/apps` CRUD + nested `/{id}/properties|views|records` (+`/search`) — `apps:app:*` + `apps:record:*` permission'ları; ErrorCode'lar `APP_*`; constraint-map `apps_name`/`app_properties_name`/`app_views_name`.
- Testler: 466 H2 (34 yeni unit + 29 yeni controller) + `AppBuilderIT` (gated gerçek PG — aktivasyon, history izolasyonu, JSONB search semantiği, iki-tenant izolasyon).

### Faz A — Kritik Güvenlik (önce)
- [x] **[P0 RISK-19]** JWT tenant binding — `JwtAuthenticationFilter`'da token tenant claim == request tenant (TenantContext) kontrolü; mismatch → SecurityContext temizle. Cross-tenant escalation kapatır.
- [x] **[P1 RISK-21]** `tokenInvalidBefore` filter kontrolü + `changePassword`/`resetPassword`/`logout`'ta `tokenInvalidBefore = now()` set. (2026-07-24)
- [x] **[P1 RISK-22]** Brute-force lockout — `failedLoginAttempts`/`lockedUntil` + `AuthService.login`'de 5 deneme/15dk backoff, `auth_account_locked` (423). **Login-scoped** (filter DB lookup RISK-21 ile). IP/tenant/email rate-limit Redis (Epic 2.6) sonrası.
- [x] **[P1 RISK-23]** RSA key prod fail-fast (`RsaKeys.resolve` prod profilinde key yoksa `IllegalStateException`).
- [x] **[P1 RISK-24]** Access token cookie `Secure: true` (`application-prod.yaml`).

> Faz A'nın 5 maddesi (19/21/22/23/24) uygulandı (2026-07-24), 139 test yeşil (H2). RISK-21: `UserRepository.findTokenInvalidBefore` tek-kolon projection + filter DB lookup (her authenticated request ekstra 1 indexed sorgu — Redis cache Epic 2.6 ile), `iat < tokenInvalidBefore` (saniyeye floor — hızlı re-login korunur), set noktaları `UserService.changePassword`/`resetPassword`/`revokeTokens` + `AuthController.logout`. **Kapsam:** user-scoped revoke (multi-device logout); granular tek-token revoke Epic 2.6. RISK-22 lockout artık `tokenInvalidBefore` set EDİYOR (Faz 1 — `AuthService.registerFailedAttempt` lock anında, kilitli hesabın access token'ları anında düşer; refresh blocked-via-accountNonLocked kalır, lock bitince tekrar çalışır). Gerçek çapraz-tenant izolasyon doğrulaması Faz B (RISK-20, Testcontainers) ile.

### Faz B — Test Altyapısı (UYGULANDI 2026-07-24)
- [x] **[P0 RISK-20]** Testcontainers + PostgreSQL ile iki gerçek tenant şeması isolation test altyapısı. RISK-19 ve RISK-26 doğrulamasının ön koşulu.
- [x] **[P1 RISK-31]** K-21 endpoint HTTP testleri (`/register` 202, `/verify`, `/suggest-subdomain`) + DELETE/{id}/PUT için 401/403 testleri.

> Faz B uygulandı (2026-07-24). `CrossTenantIsolationTest` (Testcontainers, `postgres:16-alpine`) gerçek PG'de iki tenant şeması provision edip `SET search_path` izolasyonunu + RISK-26 (mid-tx context switch) doğruladı — **Docker ile yeşil**. `-Dforgesys.pg.it=true` gate'i ile varsayılan build Docker'SIZ kalır (136 test, 2 skip). RISK-31: `AuthCompanyControllerTest` (register 202/validation, suggest-subdomain fold) + DELETE 401 testleri (3 controller). 136 test yeşil (H2).

### Faz C — K-21 Sağlamlaştırma (Faz B sonrası, gerçek PG test gerekli)
- [x] **[P1 RISK-25]** Token consumption race — conditional UPDATE (`claimToken` `@Modifying`).
- [x] **[P1 RISK-26]** Mid-tx TenantContext switch — ÇÖZÜLDÜ (2026-07-24). `createAdminUser` `@Transactional(REQUIRES_NEW)` + self-proxy; `setCurrentTenant` caller'da (`verifyAndProvision`) `self.getObject().createAdminUser(...)` çağrısından ÖNCE (resolver session açılışında okur). Gerçek PG ile doğrulandı.

> Faz C tamamlandı (2026-07-24). RISK-25: `TenantVerificationTokenRepository.claimToken` `@Modifying` conditional UPDATE (`SET usedAt=:now WHERE token=:token AND usedAt IS NULL`) — H2+PG portable, PESSIMISTIC_WRITE yerine tercih edildi. `verifyAndProvision` SELECT-validate → claim → 0 row = `TENANT_TOKEN_ALREADY_USED` (çift verify tıkı kapanır). Gerçek concurrent race Testcontainers (RISK-20) ile ayrıca doğrulanabilir; atomic UPDATE DB-invariant'ı yeterli. 140 test yeşil (H2).

### Faz D — Hata Yönetimi + Performans
- [x] **[P1 RISK-29]** `MethodArgumentTypeMismatchException` (+ `ConstraintViolationException`, `MissingServletRequestParameterException`) → 400 handler `GlobalExceptionHandler`'a.
- [x] **[P1 RISK-27]** N+1 `findAll` — `UserRepository` EntityGraph'a `userProfile`/`userAccount` ekle.
- [x] **[P2 RISK-28]** TOCTOU uniqueness — `DataIntegrityViolationException` handler + constraint name → `ErrorCode` map.

> Faz D uygulandı (2026-07-24), 124 test yeşil (H2). Malformed UUID/artık parametre artık 400 (`validation_error`), N+1 (`findAll`) kapatıldı (EntityGraph), concurrent duplicate uniqueness artık 500 değil 400 (`*_TAKEN` / `business_error`).

### Faz E — P2 Toplu Temizlik
- [ ] [RISK-30] Verification token hash-at-rest (SHA-256) + purge job + `adminPasswordHash` consume sonrası null. *(erteledi: provisioning akışına dokunuyor — gerçek PG doğrulaması (RISK-20) + `adminPasswordHash` null için migration gerekli)*
- [x] [RISK-32] `PlatformCompanyService.updateStatus` state-machine (`CompanyStatus.canTransitionTo`).
- [x] [RISK-33] AuditorAware SecurityContext userId (RISK-3'ü kapatır).
- [ ] [RISK-34] Deprecated SB4 starter'lar: `oauth2-resource-server`→`security-oauth2-resource-server`, `web`→`webmvc`, Flyway→`spring-boot-starter-flyway`. *(erteledi: build-risk, ayrı değerlendirilecek)*
- [x] AuthService timing enumeration (dummy bcrypt sabit-zamanlı compare).
- [~] GroupService.setMembers N+1 + bulk update; `findGroupMembers` `@EntityGraph`. *(EntityGraph + id dedupe done; modifying-query bulk erteledi — persistence-context riski)*
- [x] JWT `iss`/`aud` validation; security headers/CSP explicit customizer.
- [ ] `t_user_groups(group_id)` reverse index; redundant UNIQUE=PK cleanup (4 join tablosu). *(erteledi: tenant migration — "ask first")*
- [x] `ErrorCode.AUTH_TOKEN_*` wire or remove (dead code — üretilmiyor).
- [x] `CompanyResponse` `schemaName`/`dbRole` kaldır (internal sızıntı).
- [ ] `GlobalExceptionHandler` sensitive-value masking → exception message'lara da uygula. *(erteledi: mesajlarda gerçek secret leak yok; geniş masking debug'ı zorlaştırır)*

### Faz F — P3 Polisaj
- [x] N+1 `findById` EntityGraph'lar (UserService/RoleService/GroupService — `UserRepository.findById` roles+groups+profile+account, `RoleRepository.findById` permissions, `GroupRepository.findById` roles).
- [x] `resolveRoles`/`resolveGroups` duplicate-id `HashSet` dedupe.
- [x] `@ToString` token/hash/userProfile/userAccount exclude (`TenantVerificationToken`, `RefreshToken`, `User`).
- [x] `version BIGINT` → `NOT NULL DEFAULT 0`. *(K-36 pre-1.0.0 migration squash'ı — V1.x baseline ailesine gömüldü, 2026-08-22)*
- [x] `RefreshToken` ölü kod + `t_refresh_tokens` tablosu kaldır. *(K-36 — entity + repo + tablo V1.x baseline'ı dışında bırakılarak silindi; Redis-first refresh K-34'tü zaten)*
- [x] Subdomain pattern constant (DTO + service DRY — `SubdomainRules`).
- [ ] Password complexity policy (`@Pattern` mixed case/digit/symbol). *(ürün-politikası kararı; tüm test/bootstrap şifrelerini değiştirir)*
- [x] `Assign*Request` `@Size(max=...)` bound.
- [ ] `IllegalArgumentException`/`RuntimeException` → `BusinessException`/`ErrorCode` convention. *(erteledi: geniş service-layer denetimi)*
- [~] Test dummy BCrypt hash'leri düzelt; forbidden test'leri `$.code == auth_access_denied` assert. *(forbidden asserts done — 10 yer; dummy hash kozmetik bırakıldı)*
- [x] `Map<String,Object>` → `@ConfigurationProperties` (`jwt.*` cookie properties — `JwtCookieProperties` record, AuthController `@Value` x3 kaldırıldı).
- [ ] `provisionSystemTenant` self-invocation `@Transactional` no-op (proxy düzelt). *(cosmetic no-op — verifyAndProvision REQUIRED, outer tx zaten kapsıyor)*

> Faz E/F toplu temizlik (2026-07-24): yapılandırılabilir/mekanik kalemlerin çoğu uygulandı — AUTH_TOKEN ölü kod, CompanyResponse internal sızıntı, @ToString secret exclude, subdomain pattern DRY (`SubdomainRules`), Assign* `@Size`, id dedupe, AuthService timing, JWT iss/aud + security headers/CSP, CompanyStatus state-machine (RISK-32), findGroupMembers EntityGraph, forbidden `$.code` assert'leri. **126 test yeşil (H2).** Ertenlenenler: RISK-30 (provisioning + migration), RISK-34 (build-risk), password complexity (ürün kararı), tenant migration'ları (version DEFAULT / reverse index / UNIQUE=PK — "ask first"). Gerçek PG doğrulaması Faz B (RISK-20) ile.

### Sadeleştirme Planı (2026-08-22 planning session)

Analiz + kararlar: [`docs/FULL_ANALYSIS.md`](docs/FULL_ANALYSIS.md) + [`docs/ANALYSIS_ADDENDUM.md`](docs/ANALYSIS_ADDENDUM.md) · ADR'ler: [K-37..K-40](docs/DECISIONS.md). Durum: **K-38 (ölü kod kaldırma) ve K-37 (API tutarlılık geçişi) UYGULANDI** (2026-08-22; K-38 baseline V1 düzenlemesi içerir — local DB reset gerektirir). Kalan: K-40 (startup projection + tek kaynak çözümlemeler — Session 3) → K-39 (frontend kalite: strict TS + Vitest/RTL + `useListPageState`) → springdoc-openapi. Standart kararlar listesi (tekrar tartışılmaz): FULL_ANALYSIS §11 + ADDENDUM §8 (madde 21-24 dahil).

### Doğrulanan (uyumlu, aksiyon yok)

### Faz IAM — RBAC / Oturum Güvenliği (2026-07-30 başlangıç)
RBAC + oturum yönetimi IAM denetimi; fazlara bölünmüş. Tüm fazlar uygulanacak, önem sırasıyla.

- [x] **Faz 1 — Yetki-sonrası session revoke (kod-only, 2026-07-30):** `SessionRevocationService` (bulk `tokenInvalidBefore` stamp + `revokeAllForUser`). Yetkiler JWT'ye gömülü olduğu için rol/izin/grup değişimi eldeeki token'ı TTL'ince geçerli tutuyordu (privilege-retention penceresi) — kapatıldı. `UserService.setRoles`/`setGroups` (hedef user), `RoleService.setPermissions`/`delete` (`revokeRoleHolders` — soft-delete ÖNCESİ), `GroupService.setRoles`/`delete` (soft-delete öncesi) + `update(active=false)` + `setMembers` (sadece **çıkarılan** üyeler) revoke noktaları. `changePassword`/`resetPassword`/`revokeTokens` artık bu servise delege. İsim/açıklama `update` revoke etmez (yetki değişmez). **[G2.4]** `registerFailedAttempt` lock anında `tokenInvalidBefore` set (kilitli hesabın token'ları annda düşer). 253 test yeşil (H2, 13 yeni revoke testi). Dosya:ref — `security/SessionRevocationService.java`, `UserRepository.findUserIdsByRole/findUserIdsByGroup/bulkSetTokenInvalidBefore`.
- [x] **Faz 5a — Max concurrent session limit (kod-only, 2026-07-30):** `forgesys.security.max-sessions` (0=sınırsız). `SessionRevocationService.enforceSessionLimit` `AuthService.login`'de issue sonrası çağrılır; sınır aşılırsa en eski session `listSessions`/`revokeSession` ile düşürülür (login her zaman başarılı, store'a dokunulmadı). `t_sessions_log` ERTELENDİ (`t_login_history`/`t_audit_logs` ile örtüşme + paralel agent K-28 alanı). Store + `/me/sessions` + admin remote-revoke ZATEN YAPILDI (K-28, çalışma ağacında).
- [x] **Faz 2 — Audit immutable + delta (2026-07-30):** `t_audit_logs`/`t_login_history` append-only (tenant `V6` `BEFORE UPDATE/DELETE` trigger → `check_violation`; app insert-only, hiçbir şey kırmaz). `AuditService.recordDelta` + `namesJson` — `old_value`/`new_value` JSON olarak yetki değişim noktalarında (`setRoles`/`setGroups`/`setPermissions`/group `setRoles`) yakalanıyor → "kim kime hangi yetkiyi verdi/aldı" artık kayıtlı. 259 test yeşil. Kalan (K-27): `@AuditLog` AOP + high-risk `request_body` + `@ApprovalRequired`.
- [x] **Faz 3a — Rate limiting app-level (2026-07-30):** dependency-free Redis Lua token-bucket (`RedisRateLimiter`) + InMemory test fallback, `RateLimitFilter` security chain'de JWT decode'den ÖNCE çalışır; `/auth/login` + `/auth/company/verify` + `/auth/refresh` tenant+IP bazlı (`forgesys.security.rate-limit.*`, default 20/dk, Redis blip'inde fail-open). Brute-force lockout'a ek olarak credential stuffing/unknown-email yolu (G3.2) kapatıldı. 265 test yeşil. **3b Nginx `limit_req` (edge) K-33 gateway epic'ine ertelendi** (stack'te Nginx yok, premature).
- [x] **Faz 4 — RBAC inheritance + ABAC şablon (2026-07-30):** rol inheritance — `Role.parentRoles` self-M2M (`tenant/V7` `t_role_parents`) + `RoleService.setParents` (cycle guard: self-parent + reaches-back rejected, `ROLE_PARENT_CYCLE` 400) + revoke holders + audit delta; `resolveAuthorities` artık parent rolleri **recursive** (visited-set guard — cycle-safe) traverse ediyor. `PUT /api/v1/roles/{id}/parents` (`iam:role:write`); `RoleResponse.parents`. ABAC service-layer şablonu: `Ownable` interface (persistence) + `OwnershipGuard.assertOwner` (backend, 403) — Notes/Warehouse/Logistics için hazır, Task/Project'e uygulanmadı. *(Şablon K-38 ile kaldırıldı — kullanılmıyordu; ilk ABAC ihtiyacı olan modülle geri gelir.)* **Permission resource/action normalize YAPILMADI** (düşük değer — flat `name` + `PermissionCatalog` zaten `{module}:{resource}:{action}` kuralını uyguluyor). 274 test yeşil. Dosya:ref — `entity/Role.java` (parentRoles), `security/CustomUserDetailsService.java`, `security/OwnershipGuard.java`, `entity/Ownable.java`, `RoleService.setParents`.
- [x] **`all_permissions` flag — Admin implicit süper-kullanıcı + "ALL" rol kısayolu (2026-07-31, K-35):** `t_roles.all_permissions` (tenant `V8`). Bir rol flag'i taşıyorsa `CustomUserDetailsService.resolvePermissionNames` parent-closure'dan sonra tenant'taki **tüm** permission isimlerini döndürür (`PermissionRepository.findAllNames` JPQL projection) — `t_role_permissions` satırı olmadan. **İki kullanım tek mekanizma:** (1) `RbacSeeder` Admin'e `all_permissions=true` set eder + explicit permission satırlarını temizler → runtime permission'lar Admin'e otomatik ulaşır (eski bug: hiç ulaşmıyordu), katalog permission silme Admin yüzünden `in_use` bloğa takılmaz; (2) `PUT /roles/{id}/permissions` artık `{all:true}` ("ALL" kısayolu) veya `{permissionIds:[...]}` kabul eder, `RoleResponse.allPermissions` expose eder. **İmmediacy:** `PermissionService.create`/rename `SessionRevocationService.revokeAllPermissionsRoleHolders` çağırır → all-permissions kullanıcıların token'ı refresh eder. `@PreAuthorize` enforcement + frontend dokunulmadı. 302 test yeşil. Dosya:ref — `entity/Role.java` (allPermissions), `security/CustomUserDetailsService.java`, `security/SessionRevocationService.java`, `config/RbacSeeder.java`, `service/RoleService.setPermissions`, `service/PermissionService.create`.
- [x] **Last-admin invariant (2026-08-15, [RISK-35](docs/DECISIONS.md#risk-35)):** hiçbir tenant son aktif admin'ini kaybedemez. `security/LastAdminGuard` — `assertNotSelf` (self-delete koşulsuz 409 `self_delete_forbidden`) + `assertActiveAdminExists` (post-mutation: ≥1 enabled, soft-delete olmamış, effective closure'ında `all_permissions` rolü olan user kalmalı, yoksa 409 `last_admin_required`; admin-closure = flag rolleri + `t_role_parents` aşağı-BFS). 11 write path'e wired (`UserService.delete/update(enabled=false)/setRoles/setGroups`, `RoleService.delete/setPermissions/setParents`, `GroupService.update(active=false)/delete/setRoles/setMembers`); guard revoke'dan önce → reddedilen işlem Redis hasarı bırakmaz. Side-fix'ler: login artık `enabled` kontrol eder (401 `auth_account_disabled`), disable/delete `revokeUser` çağırır (token'lar anında düşer). Role/group delete'te join satırları önce koleksiyon mutasyonuyla temizlenir (latent `TransientPropertyValueException` fix). Frontend kendi satırında Delete'i gizler. 324 test yeşil (H2).
- [x] **User directory read model + scoped görünürlük + admin unlock (2026-08-17):** `GET /users` + `POST /users/search` artık `UserDirectoryView` (`@Immutable @Subselect` — Hibernate derived table, migration YOK, H2+PG portable) üzerinden düz `UserDirectoryViewResponse` dönüyor: profil join + `roleCount`/`groupCount` DB'de çözümlenir → N+1 yapısal olarak yok. **Wire değişikliği:** liste `roles[]`/`groups[]` yerine count döner (bilinçli, belgeli; detay endpoint'leri tam `UserResponse` dönmeye devam eder). Yeni izin `iam:group-member:read`: kendi gruplarının üyeleri + self görünürlüğü (`UserService.applyVisibilityScope` liste `IN` predicate + `assertViewable` detail'e 403). `GET /users/{id}/activity` (audit stamp'ler + son login/başarısız deneme), `DELETE /users/{id}/lock` (admin unlock, `iam:user:write`), audit/login-history `q` araması.
- [x] **Güvenlik düzeltmeleri (2026-08-16/17, [RISK-36](docs/DECISIONS.md#risk-36)):** (1) **RbacSeeder startup privilege escalation kapandı** — eski kod her restart'ta rol'süz kullanıcıları all-permissions `Admin`'e atıyordu; Admin artık yalnızca provisioning'de explicit `assignAdminTo(user)` ile verilir (`RbacSeederTest` regresyonu). (2) Aktif `lockedUntil` penceresi refresh'te de bloklu (`CustomUserDetails.isEffectivelyNonLocked` — kilitli hesap refresh ile yeni access token basamaz). (3) `RedisRefreshTokenStore.revoke` rotasyon zincirini (`rotatedTo`) takip ediyor (logout↔rotate yarışı). **Bilinen açık P2'ler:** Redis kesintisinde fail-closed davranış (rate-limit/blacklist "fail-open" yorumu fiilen exception → login/refresh/jti'li request 500); `InMemoryRefreshTokenStore.revoke` zincir takip etmiyor (test-parite); revoke zincir yürüyüşü Redis'te de atomik değil.


- Jackson 3 (`tools.jackson.*`), yeni `@EntityScan` paketi, `@EnableMethodSecurity`, `authorizeHttpRequests`+`requestMatchers`, literal `-100` filter order, `GenerationType.UUID`, `@SQLRestriction` (deprecated `@Where` değil), `TIMESTAMPTZ` uzun form.
- `PepperingPasswordEncoder` (K-23) sound — HMAC-SHA256 pre-hash + BCrypt(12), pepper log'lanmıyor, lazy rehash doğru.
- `TenantFilter` ordering (-101, security öncesi) doğru; SQL injection defense (schema regex `^[a-z0-9_]+$`) defense-in-depth.
- Soft-delete masking (`sanitizeRejectedValue` field errors), `ddl-auto=none` her yerde, `tokenInvalidBefore` gap doğrulandı (belgeli).

## Test

- Config profiles (dev/prod/test, H2, ddl-auto, flyway.enabled) are the single source: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#konfigürasyon-profilleri).
- Add at least one test per new endpoint. Extra care on changes touching tenant isolation.

## Limits

**Never:**
- **Run git operations without authorization.** `git commit`, `git push`, `git amend`, `git merge`, `git rebase`, `git reset --hard`, branch creation/deletion, `gh pr create`, etc. — ALL of these happen ONLY when the user explicitly asks. Do NOT take initiative like "I'm done, let me commit." Stay in staging until the user says commit/push. `git add`/`git status`/`git diff`/`git log` (read-only) are fine.
- Do not commit/read `.env`, `application-prod.yaml` secrets, or RSA keys (`certs/*.pem`).
- Do not set `ddl-auto` to `validate` (multi-tenant + lazy tenant schema crashes at startup — always `none`, the schema lives in Flyway). Test profile exception: `create-drop`.
- Do not write cross-tenant queries. Do not log sensitive data (password, token, PII).

**Ask first:**
- Before adding a new Flyway migration (especially if it affects existing tenant schemas — `TenantMigrationRunner` is required, see [RISK-16](docs/DECISIONS.md#risk-16)).
- Before adding a new dependency (first check whether the root pom accommodates it).

**Always:**
- Add a test for a new endpoint.
- Use `@Transactional` (method-level; `readOnly=true` for lookups) for service-layer write operations. **Exception:** `provisionTenant` is currently non-transactional ([DEBT-10](docs/DECISIONS.md#debt-10)); fixed with K-21.

## Git

> **The rules below apply ONLY when the user explicitly asks for a commit/push/PR.** An agent must not commit, push, amend, merge, create/delete a branch, or open a PR on its own — see "Limits / Never" above. Leave changes in staging; do not act until the user says `git add`/`commit`/`push`/`gh pr create`.

- **Branch:** `feat/SF-NN-kisa-aciklama` — the developer chooses their own `SF-NN` number; it is not tied to the roadmap. Branch is deleted after merge.
- **Commit:** Conventional Commits — `feat(tenant): add subdomain resolver`, `fix(auth): handle expired token`, `refactor: ...`, `test: ...`, `docs: ...`, `chore(deps): ...`. Subject <72 chars, lowercase, no period, imperative mood.
- All PRs target `develop`. Squash merge. Before a PR: `./mvnw test` + `npm run lint`.
