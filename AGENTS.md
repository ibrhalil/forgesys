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
- `infra/data/{postgres,redis}/` — bind-mount volume. **Not committed.** On macOS, for permission issues: postgres UID 70, redis UID 999.
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

### Faz A — Kritik Güvenlik (önce)
- [x] **[P0 RISK-19]** JWT tenant binding — `JwtAuthenticationFilter`'da token tenant claim == request tenant (TenantContext) kontrolü; mismatch → SecurityContext temizle. Cross-tenant escalation kapatır.
- [x] **[P1 RISK-21]** `tokenInvalidBefore` filter kontrolü + `changePassword`/`resetPassword`/`logout`'ta `tokenInvalidBefore = now()` set. (2026-07-24)
- [x] **[P1 RISK-22]** Brute-force lockout — `failedLoginAttempts`/`lockedUntil` + `AuthService.login`'de 5 deneme/15dk backoff, `auth_account_locked` (423). **Login-scoped** (filter DB lookup RISK-21 ile). IP/tenant/email rate-limit Redis (Epic 2.6) sonrası.
- [x] **[P1 RISK-23]** RSA key prod fail-fast (`RsaKeys.resolve` prod profilinde key yoksa `IllegalStateException`).
- [x] **[P1 RISK-24]** Access token cookie `Secure: true` (`application-prod.yaml`).

> Faz A'nın 5 maddesi (19/21/22/23/24) uygulandı (2026-07-24), 139 test yeşil (H2). RISK-21: `UserRepository.findTokenInvalidBefore` tek-kolon projection + filter DB lookup (her authenticated request ekstra 1 indexed sorgu — Redis cache Epic 2.6 ile), `iat < tokenInvalidBefore` (saniyeye floor — hızlı re-login korunur), set noktaları `UserService.changePassword`/`resetPassword`/`revokeTokens` + `AuthController.logout`. **Kapsam:** user-scoped revoke (multi-device logout); granular tek-token revoke Epic 2.6. RISK-22 lockout `tokenInvalidBefore` set ETMEZ (kilitlenen hesabın elindeki token TTL'ince geçerli — lockout-anında-revoke erteli). Gerçek çapraz-tenant izolasyon doğrulaması Faz B (RISK-20, Testcontainers) ile.

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
- [ ] `version BIGINT` → `NOT NULL DEFAULT 0` (migration). *(erteledi: tenant migration)*
- [ ] `RefreshToken` ölü kod + `t_refresh_tokens` tablosu kaldır (Epic 2.5 gelince ekle). *(bırakıldı: Epic 2.5 tekrar ekleyecek, churn önlenir)*
- [x] Subdomain pattern constant (DTO + service DRY — `SubdomainRules`).
- [ ] Password complexity policy (`@Pattern` mixed case/digit/symbol). *(ürün-politikası kararı; tüm test/bootstrap şifrelerini değiştirir)*
- [x] `Assign*Request` `@Size(max=...)` bound.
- [ ] `IllegalArgumentException`/`RuntimeException` → `BusinessException`/`ErrorCode` convention. *(erteledi: geniş service-layer denetimi)*
- [~] Test dummy BCrypt hash'leri düzelt; forbidden test'leri `$.code == auth_access_denied` assert. *(forbidden asserts done — 10 yer; dummy hash kozmetik bırakıldı)*
- [x] `Map<String,Object>` → `@ConfigurationProperties` (`jwt.*` cookie properties — `JwtCookieProperties` record, AuthController `@Value` x3 kaldırıldı).
- [ ] `provisionSystemTenant` self-invocation `@Transactional` no-op (proxy düzelt). *(cosmetic no-op — verifyAndProvision REQUIRED, outer tx zaten kapsıyor)*

> Faz E/F toplu temizlik (2026-07-24): yapılandırılabilir/mekanik kalemlerin çoğu uygulandı — AUTH_TOKEN ölü kod, CompanyResponse internal sızıntı, @ToString secret exclude, subdomain pattern DRY (`SubdomainRules`), Assign* `@Size`, id dedupe, AuthService timing, JWT iss/aud + security headers/CSP, CompanyStatus state-machine (RISK-32), findGroupMembers EntityGraph, forbidden `$.code` assert'leri. **126 test yeşil (H2).** Ertenlenenler: RISK-30 (provisioning + migration), RISK-34 (build-risk), password complexity (ürün kararı), tenant migration'ları (version DEFAULT / reverse index / UNIQUE=PK — "ask first"). Gerçek PG doğrulaması Faz B (RISK-20) ile.

### Doğrulanan (uyumlu, aksiyon yok)
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
