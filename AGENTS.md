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
- [ ] **[P0 RISK-19]** JWT tenant binding — `JwtAuthenticationFilter`'da token tenant claim == request tenant (TenantContext) kontrolü; mismatch → SecurityContext temizle. Cross-tenant escalation kapatır.
- [ ] **[P1 RISK-21]** `tokenInvalidBefore` filter kontrolü + `changePassword`/`resetPassword`/`logout`'ta `tokenInvalidBefore = now()` set.
- [ ] **[P1 RISK-22]** Brute-force lockout (`failedLoginAttempts`/`lockedUntil` kullan + rate-limit IP/tenant/email bazlı).
- [ ] **[P1 RISK-23]** RSA key prod fail-fast (`RsaKeys.resolve` prod profilinde key yoksa `IllegalStateException`).
- [ ] **[P1 RISK-24]** Access token cookie `Secure: true` (`application-prod.yaml`).

### Faz B — Test Altyapısı (kullanıcı erteledi, kritik)
- [ ] **[P0 RISK-20]** Testcontainers + PostgreSQL ile iki gerçek tenant şeması isolation test altyapısı. RISK-19 ve RISK-26 doğrulamasının ön koşulu.
- [ ] **[P1 RISK-31]** K-21 endpoint HTTP testleri (`/register` 202, `/verify`, `/suggest-subdomain`) + DELETE/{id}/PUT için 401/403 testleri.

### Faz C — K-21 Sağlamlaştırma (Faz B sonrası, gerçek PG test gerekli)
- [ ] **[P1 RISK-25]** Token consumption race — `findByTokenForUpdate` (PESSIMISTIC_WRITE) veya conditional UPDATE.
- [x] **[P1 RISK-26]** Mid-tx TenantContext switch — ÇÖZÜLDÜ (2026-07-24). `createAdminUser` `@Transactional(REQUIRES_NEW)` + self-proxy; `setCurrentTenant` caller'da (`verifyAndProvision`) `self.getObject().createAdminUser(...)` çağrısından ÖNCE (resolver session açılışında okur). Gerçek PG ile doğrulandı.

### Faz D — Hata Yönetimi + Performans
- [ ] **[P1 RISK-29]** `MethodArgumentTypeMismatchException` (+ `ConstraintViolationException`, `MissingServletRequestParameterException`) → 400 handler `GlobalExceptionHandler`'a.
- [ ] **[P1 RISK-27]** N+1 `findAll` — `UserRepository` EntityGraph'a `userProfile`/`userAccount` ekle.
- [ ] **[P2 RISK-28]** TOCTOU uniqueness — `DataIntegrityViolationException` handler + constraint name → `ErrorCode` map.

### Faz E — P2 Toplu Temizlik
- [ ] [RISK-30] Verification token hash-at-rest (SHA-256) + purge job + `adminPasswordHash` consume sonrası null.
- [ ] [RISK-32] `PlatformCompanyService.updateStatus` state-machine (`CompanyStatus.canTransitionTo`).
- [ ] [RISK-33] AuditorAware SecurityContext userId (RISK-3'ü kapatır).
- [ ] [RISK-34] Deprecated SB4 starter'lar: `oauth2-resource-server`→`security-oauth2-resource-server`, `web`→`webmvc`, Flyway→`spring-boot-starter-flyway`.
- [ ] AuthService timing enumeration (dummy bcrypt sabit-zamanlı compare).
- [ ] GroupService.setMembers N+1 + bulk update; `findGroupMembers` `@EntityGraph`.
- [ ] JWT `iss`/`aud` validation; security headers/CSP explicit customizer.
- [ ] `t_user_groups(group_id)` reverse index; redundant UNIQUE=PK cleanup (4 join tablosu).
- [ ] `ErrorCode.AUTH_TOKEN_*` wire or remove (dead code — üretilmiyor).
- [ ] `CompanyResponse` `schemaName`/`dbRole` kaldır (internal sızıntı).
- [ ] `GlobalExceptionHandler` sensitive-value masking → exception message'lara da uygula.

### Faz F — P3 Polisaj
- N+1 `findById` EntityGraph'lar (UserService/RoleService).
- `resolveRoles`/`resolveGroups` duplicate-id `HashSet` dedupe.
- `@ToString` token/hash/userProfile/userAccount exclude (`TenantVerificationToken`, `RefreshToken`, `User`).
- `version BIGINT` → `NOT NULL DEFAULT 0` (migration).
- `RefreshToken` ölü kod + `t_refresh_tokens` tablosu kaldır (Epic 2.5 gelince ekle).
- Subdomain pattern constant (DTO + service DRY).
- Password complexity policy (`@Pattern` mixed case/digit/symbol).
- `Assign*Request` `@Size(max=...)` bound.
- `IllegalArgumentException`/`RuntimeException` → `BusinessException`/`ErrorCode` convention.
- Test dummy BCrypt hash'leri düzelt; forbidden test'leri `$.code == auth_access_denied` assert.
- `Map<String,Object>` → `@ConfigurationProperties` (`jwt.*` cookie properties).
- `provisionSystemTenant` self-invocation `@Transactional` no-op (proxy düzelt).

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
