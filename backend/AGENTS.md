# backend/AGENTS.md

## Module

Spring Boot application — controller/service/security/config. Depends on `common` + `persistence`. Only this module produces an executable jar (`forgesys-backend.jar`). General rules from the root AGENTS.md apply.

For commands see [README](../README.md#build-komutları). Backend summary: `./mvnw -pl backend spring-boot:run` (NOT from root, always `-pl backend`), `./mvnw -pl backend test -Dtest=ClassName#method`.

## Package layout

Root package `com.ibrhalil.forgesys` (NOT a `.backend` subpackage):
- `ForgeSysApplication` — main.
- `tenant/` — `TenantFilter` (subdomain resolution).
- `web/` — `RequestMetadataFilter` (per-request traceId / client IP / User-Agent) + `RequestContext` / `RequestMeta` ThreadLocal holder (K-19).
- `controller/` — REST (`/api/v1/*`). `[PHASE 3]` `modules/` subpackage.
- `service/` — business logic: `TenantProvisioningService`, `TenantMigrationSupport` (shared programmatic tenant Flyway), `AuthService`, `UserService`/`RoleService`/`GroupService`/`PermissionService` (tenant-scoped RBAC), `PlatformCompanyService` (cross-tenant), `AuditService`/`AuditQueryService`/`LoginHistoryService` (K-19 audit). `[PHASE 3]` `modules/` subpackage.
- `dto/` — request/response DTOs (`record`).
- `exception/` — `GlobalExceptionHandler`, uniform error shape (`ApiErrorResponse`/`ApiFieldError`/`ApiErrorFactory`), `ErrorCode` (stable wire codes), `BusinessException` -> `AuthException`/`ResourceNotFoundException` hierarchy.
- `security/` — Spring Security adapters: `RestAuthenticationEntryPoint` (401), `RestAccessDeniedHandler` (403), `PepperingPasswordEncoder` (K-23), `CustomUserDetails`/`CustomUserDetailsService`, `jwt/` (`JwtConfig`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `RsaKeyProperties`, `RsaKeys`).
- `config/` — `MultiTenancyJpaConfig`, `SecurityConfig` (`@EnableMethodSecurity` + filter chain + BCrypt/pepper + tenant-filter ordering), `CorsConfig`, `TenantMigrationRunner` (`ApplicationRunner`, `@Profile("!test")`), `RbacSeeder` (`ApplicationRunner`, `@Profile("!test")`), `PermissionCatalog` (built-in permission namespace), `SystemAdminBootstrapRunner` + `SystemAdminBootstrapProperties` (K-24).

## Tenant Context rules (CRITICAL)

- `TenantFilter` (`OncePerRequestFilter`): Host header -> subdomain -> `CompanyRepository.findBySubdomain` -> `schemaName` -> `TenantContext`. Only `ACTIVE` Companies are resolved (`PROVISIONING`/`SUSPENDED`/`TERMINATED` are not).
- `shouldNotFilter()` exempts only `/api/v1/auth/company/**` (tenant creation — no tenant yet) and `/actuator/**`. Login (`/api/v1/auth/login`) and `/me` ARE tenant-specific and go through normal subdomain resolution.
- **Do NOT validate tenant in the controller** — the filter is the single responsibility owner.
- When tenant context is null the resolver returns `"public"` -> public-schema data (Company) is reachable.
- Services that programmatically set the tenant (e.g. `TenantProvisioningService.provisionTenant` -> `createAdminUser`) MUST call `TenantContext.clear()` in `finally`.
- The `X-Tenant-ID` header fallback is active **only in the `dev` profile**. Fully disabled in prod.
- `TenantContext` is NOT propagated automatically across `@Async` threads — a `TaskDecorator` is required ([RISK-10](../docs/DECISIONS.md#risk-10)). **Deferred** to after Phase 2.3 auth (no `@Async` consumer yet; `@EnableAsync` absent).

## Endpoint rules

- All endpoints live under the `/api/v1/*` prefix.
- Return a response DTO (`record`); entities MUST NOT be exposed directly. **Current exception:** `AuthController.registerCompany()` returns `ResponseEntity<Map<String,Object>>` — MapStruct + DTO refactor happens in Epic 2.1 ([ROADMAP](../docs/ROADMAP.md)).
- Errors use the uniform `ApiErrorResponse` (`GlobalExceptionHandler`): every error path returns `{timestamp, status, error, code, message, path, traceId, fields[]}`. `code` is a stable `ErrorCode` (lowercased enum name, e.g. `auth_bad_credentials`) — clients branch on it; message/status may evolve. Sensitive rejected values (`password`/`token`/`secret`/`credential`) are masked to `[REDACTED]` in field errors. `BusinessException` (`AuthException`/`ResourceNotFoundException`) carries an `ErrorCode`; `TenantNotFoundException` stays a plain `RuntimeException` in `common` and is translated by the handler. `traceId` comes from MDC, populated per request by `RequestMetadataFilter` (K-19; honors `X-Request-Id` or generates a UUID); before that filter runs, `ApiErrorFactory` generates one per error. Client-error mappings (Faz D): malformed path/query params (`MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `jakarta.validation.ConstraintViolationException`) → 400 `validation_error`; concurrent uniqueness races (`DataIntegrityViolationException`) → 400 with the precise `*_TAKEN` code (constraint-name substring map: `users_email`→`user_email_taken` etc.) or `business_error` fallback — never 500.
- Bean Validation (`@Valid` + `@NotBlank`/`@Pattern`/`@Email`).

### Current endpoints

> RBAC enforcement via `@EnableMethodSecurity` (`SecurityConfig`) + `@PreAuthorize` (K-26). Permission namespace `{module}:{resource}:{action}` — see `PermissionCatalog` for the seeded catalog (iam:* + platform:*) and `RbacSeeder` for the seeding logic.

**Public (no auth, `SecurityConfig.permitAll`):**

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/auth/company/register` | K-21 phase 1 — creates a `PROVISIONING` Company + `TenantVerificationToken` and emails the verification link. 202 Accepted (tenant not ready yet). NO schema/Flyway/admin user. |
| `POST` | `/api/v1/auth/company/verify` | K-21 phase 2 — consumes the token, runs `CREATE SCHEMA` + Flyway tenant migration + admin user, promotes Company to `ACTIVE`. 200 OK. |
| `POST` | `/api/v1/auth/company/suggest-subdomain` | K-21 — slug candidates (Turkish-aware) for an org name; up to 3 unique suggestions. |
| `POST` | `/api/v1/auth/login` | Email+password → RS256 access token + opaque refresh token. Cookies (`sf_access_token`, `sf_refresh_token`) + body. Tenant resolved by subdomain. Unknown/bad-password both → `401 auth_bad_credentials` (no enumeration). |
| `POST` | `/api/v1/auth/refresh` | Rotates the refresh token (cookie or body) and mints a fresh access token (K-34). Public; tenant from `TenantFilter`; authorities re-resolved from DB. Reuse of a consumed token → `401 auth_refresh_token_reuse` (all sessions revoked). |
| `GET` | `/actuator/health/**`, `/actuator/info` | Health/info (prod exposes only health). |

**Authenticated self-service (any logged-in user, no `iam:*` permission):**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/auth/me` | Current user (id/email/tenant/authorities) from the JWT cookie — no DB hit. |
| `POST` | `/api/v1/auth/logout` | Per-session logout (K-34): consumes this device's refresh token + blacklists the current access token's `jti` (granular revoke). Expires both cookies. Other devices keep working. |
| `GET` | `/api/v1/users/me` | Self profile (delegates to `UserService.findById(principal.userId)`). |
| `PUT` | `/api/v1/users/me/profile` | Update own profile (firstName/lastName/phone). |
| `PUT` | `/api/v1/users/me/password` | Change own password (current password verified). |

**Tenant-scoped admin (RBAC, `iam:*` — `@PreAuthorize`):**

| Method | Path | Permission |
|--------|------|------------|
| `GET` | `/api/v1/users` (page) · `GET /{id}` | `iam:user:read` |
| `POST` | `/api/v1/users` · `PUT /{id}` · `PUT /{id}/roles` · `PUT /{id}/groups` · `PATCH /{id}/password` | `iam:user:write` |
| `DELETE` | `/api/v1/users/{id}` | `iam:user:delete` |
| `GET` | `/api/v1/roles` (page) · `GET /{id}` | `iam:role:read` |
| `POST` | `/api/v1/roles` · `PUT /{id}` · `PUT /{id}/permissions` | `iam:role:write` |
| `DELETE` | `/api/v1/roles/{id}` | `iam:role:delete` |
| `GET` | `/api/v1/groups` (page) · `GET /{id}` | `iam:group:read` |
| `POST` | `/api/v1/groups` · `PUT /{id}` · `PUT /{id}/roles` · `PUT /{id}/members` | `iam:group:write` |
| `DELETE` | `/api/v1/groups/{id}` | `iam:group:delete` |
| `GET` | `/api/v1/permissions` (list) | `iam:permission:read` |

> `PermissionController` is read-only (list). The permission catalog is seed-driven (`PermissionCatalog` + `RbacSeeder`), not user-creatable.

**Platform-scoped (cross-tenant, `platform:*` — K-25):**

| Method | Path | Permission |
|--------|------|------------|
| `GET` | `/api/v1/platform/companies` · `GET /{id}` | `platform:company:read` |
| `PATCH` | `/api/v1/platform/companies/{id}/status` | `platform:company:write` |

> `PlatformCompanyService` clears `TenantContext` (`executeWithoutTenantContext`) to query the `public` schema — lists/gets all `t_companies`. Status update drives `CompanyStatus` lifecycle (ACTIVE → SUSPENDED → TERMINATED); full lifecycle (billing-driven) arrives in Faz 6.

**Audit & log (tenant, `iam:audit:read` — K-19):**

| Method | Path | Permission |
|--------|------|------------|
| `GET` | `/api/v1/audit-logs` (page; `?action=` / `?actorId=`) | `iam:audit:read` |
| `GET` | `/api/v1/login-history` (page; `?userId=` / `?success=`) | `iam:audit:read` |

> `AuditQueryService` reads `t_audit_logs` + `t_login_history` (tenant schema), paged newest-first with optional first-match filters. `iam:audit:read` is seeded into the `Admin` role via `PermissionCatalog` (`RbacSeeder` re-syncs on startup).

> **Auth stack (Faz 2.3/2.4/2.5/2.9 — DONE):** RS256 JWT via `spring-boot-starter-oauth2-resource-server`. `JwtTokenProvider` mints tokens (sub=userId, claims: email/tenant/authorities). `JwtAuthenticationFilter` reads the cookie, decodes, rebuilds `CustomUserDetails` from claims, sets `SecurityContext` (no DB per request). `CustomUserDetailsService` (login only) resolves authorities = direct roles + active group roles → permissions. The oauth2 auto-config filter is NOT enabled (custom filter — [RISK-14](../docs/DECISIONS.md#risk-14)). RSA keys: configured PEMs in prod, **ephemeral** in dev/test (warning logged) — never commit `certs/*.pem`. Method security is enabled (`@EnableMethodSecurity` + `@PreAuthorize`, [K-26](../docs/DECISIONS.md#k-26)); all `iam:*` / `platform:*` endpoints are guarded. Self-service `/users/me/**` is authenticated-only (no `iam:*` permission).

> **Deferred (Epic 2.5 rest / 2.6):** ~~refresh tokens + logout (Redis blacklist — granular per-session revoke)~~ **DONE (K-34, 2026-07-30).** **Token revocation DONE (RISK-21 + K-34):** `JwtAuthenticationFilter` reads `UserAccount.tokenInvalidBefore` (single-column projection `UserRepository.findTokenInvalidBefore`) per request and rejects tokens whose `iat` (floored to seconds) strictly predates it — **user-scoped** revoke. Set by `UserService.changePassword` / `resetPassword` / `invalidateTokens` (refresh reuse). **Granular per-token revoke (K-34):** access tokens carry a `jti`; `TokenBlacklistService` (Redis `bl:jti:{jti}`, TTL = access lifetime) blacklists a single token on per-session logout, and the filter checks it after the tenant/tokenInvalidBefore gates. **Refresh (K-34):** `AuthService.login` issues an opaque refresh token (Redis, SHA-256 hash-at-rest, atomic Lua rotation + reuse detection); `POST /api/v1/auth/refresh` rotates + re-resolves authorities; reuse → revoke all + `tokenInvalidBefore`. Per-session `logout` consumes the refresh + blacklists the access `jti` (no `tokenInvalidBefore`). Store impls are `@Profile`-split (`RedisRefreshTokenStore`/`RedisTokenBlacklistService` dev-prod, `InMemory*` test — Docker-free build); real Redis verified by gated `RedisRefreshTokenIT` (`-Dforgesys.redis.it=true`). Deferred: tenant içi user email doğrulama + password reset akış (entity field'ları hazır: `emailVerificationToken`/`passwordResetToken`); PermissionCacheService (yetkiler JWT'de gömülü, düşük değer). **Brute-force lockout ([RISK-22]) DONE (login-scoped):** `AuthService.login` counts `failedLoginAttempts`/`lockedUntil` (5/15dk → `auth_account_locked` 423); a locked account cannot refresh either (refresh re-resolves account flags). The locked account's outstanding access tokens stay valid until TTL (RISK-22 does not stamp `tokenInvalidBefore`).

> **Two-phase signup flow (K-21) — IMPLEMENTED:** `POST /api/v1/auth/company/register` returns 202 with a `PROVISIONING` Company + issues a `TenantVerificationToken` (admin credentials pre-hashed into the token). `POST /api/v1/auth/company/verify` consumes the token, runs `CREATE SCHEMA` + programmatic Flyway + admin user, and promotes the Company to `ACTIVE`. `TenantProvisioningService` is split into `createPendingCompany` + `verifyAndProvision` (both `@Transactional`; DDL inside `verifyAndProvision` is an implicit commit, see [DEBT-10](../docs/DECISIONS.md#debt-10) partial). `provisionSystemTenant` is the bootstrap auto-verify path (no mail). Details in [DECISIONS.md K-21](../docs/DECISIONS.md#k-21) + [K-32](../docs/DECISIONS.md#k-32) (email_domain dropped, 1:N org domains).

> **Audit & log (K-19 core) — IMPLEMENTED (2026-07-27):** 3-layer log. Layer 1 (audit log): `AuditService.record(action, entityType, entityId, entityName)` writes `t_audit_logs` (actor from SecurityContext, IP/traceId from `RequestContext`) on every admin write across User/Role/Group/PlatformCompany. Layer 2 (login history): `LoginHistoryService.record(...)` writes `t_login_history` for every login attempt (success + failure; `reason` = `ErrorCode.code()`) at each `AuthService.login` outcome. Layer 3 (request/trace): `RequestMetadataFilter` (`-102` order) populates `RequestContext` + MDC `traceId` (stable per request). Read side: `GET /audit-logs` + `GET /login-history` (`iam:audit:read`, paged + filtered). All writes `REQUIRES_NEW` + best-effort. Remaining: request/trace **table** (`GET /request-logs`) and K-27 (old/new value, high-risk body, `@AuditLog` AOP, approval workflow, anomaly). See [DECISIONS.md K-19](../docs/DECISIONS.md#k-19).

## Service layer

- **`TenantProvisioningService`** (K-21 two-phase flow):
  - `createPendingCompany(request)` `@Transactional` — validates uniqueness (subdomain + schemaName; `email_domain` removed K-32), inserts a `PROVISIONING` `Company` + a `TenantVerificationToken` (admin credentials pre-hashed), hands the verification URL to `VerificationSender`. Light — no DDL/Flyway/admin.
  - `verifyAndProvision(token)` `@Transactional` — finds+validates the token (`TENANT_TOKEN_INVALID`/`EXPIRED`/`ALREADY_USED`), runs `CREATE SCHEMA` (raw JDBC, `IF NOT EXISTS`) + `TenantMigrationSupport.migrateSchema` + `createAdminUser`, flips Company to `ACTIVE`, sets `token.usedAt`. Heavy/synchronous — DDL is an implicit commit so the transactional annotation is best-effort ([DEBT-10](../docs/DECISIONS.md#debt-10) partial).
  - `provisionSystemTenant(request)` `@Transactional` — bootstrap (K-24) auto-verify: runs phase 1 (no mail) + phase 2 in one call. Used by `SystemAdminBootstrapRunner`.
  - DDL (`CREATE SCHEMA`) is an implicit commit in PostgreSQL → escapes the transaction. Partial-write recovery is idempotency, not rollback.
  - `createAdminUser` sets `TenantContext` for the new schema, persists the admin user + RBAC seed (`RbacSeeder.seedForCurrentTenant`, no-op in `test`), clears context in `finally`.
- Lookups should use `@Transactional(readOnly=true)`. Writes use method-level `@Transactional`.
- **`SubdomainSuggestionService`** — `suggest(name)` slugifies (Turkish-aware ASCII fold) + validates pattern + confirms availability against `t_companies`; returns up to 3 candidates (primary + `-2`, `-3` suffixes if taken). `@Transactional(readOnly=true)`.
- **`UserService` / `RoleService` / `GroupService` / `PermissionService`** — tenant-scoped RBAC CRUD. All write operations `@Transactional`; soft-delete via `@SQLDelete`; `PermissionCacheService` eviction (when Redis lands in Epic 2.6) will fire on role/group mutations.
- **`PlatformCompanyService`** (K-25) — cross-tenant operations on `public.t_companies`. `findAll`/`findById`/`updateStatus`. Uses `executeWithoutTenantContext` (clears `TenantContext` for the duration of the op, restores afterward) so queries hit the `public` schema directly.
- **`AuthService.login`** — read-write `@Transactional` (lazy pepper rehash, [K-23](../docs/DECISIONS.md#k-23)). Records every login attempt (success + failure, reason = `ErrorCode.code()`) to `t_login_history` via `LoginHistoryService` (K-19).
- **`AuditService`** (K-19 layer 1) — `record(action, entityType, entityId, entityName)` writes `t_audit_logs` (actor id+email from SecurityContext, IP+traceId from `RequestContext`). `@Transactional(REQUIRES_NEW)` + best-effort: audit logging never breaks the business op. Wired into User/Role/Group/PlatformCompany write methods (explicit calls; `@AuditLog` AOP aspect is a K-27 follow-up — AOP infra already on classpath via security).
- **`LoginHistoryService`** (K-19 layer 2) — `record(userId, username, success, reason)` writes `t_login_history`; `REQUIRES_NEW` + best-effort. Called by `AuthService.login` at each outcome (unknown email → `userId=null`).
- **`AuditQueryService`** (K-19 read side) — paged, newest-first views over `t_audit_logs` + `t_login_history` with optional first-match filters (action/actorId; userId/success). `@Transactional(readOnly=true)`, maps entities → response records.
- **`TenantMigrationRunner`** (`config/`, `ApplicationRunner`, `@Profile("!test")`) — at startup, iterates `t_companies` (public schema, no tenant context) and runs `TenantMigrationSupport.migrateSchema(schemaName)` per tenant. Applies new tenant migrations (`tenant/V2`, `V3`, ...) to EXISTING tenants ([RISK-16](../docs/DECISIONS.md#risk-16) — RESOLVED). Per-tenant try/catch: one broken schema doesn't abort others. Disabled in `test` profile (H2 + flyway off).
- **`RbacSeeder`** (`config/`, `ApplicationRunner`, `@Profile("!test")`) — at startup, iterates `t_companies` and per tenant (via `TenantContext` switch) ensures the full `PermissionCatalog` + an `Admin` role carrying every permission + assigns `Admin` to any role-less user. Idempotent (re-syncs Admin permissions on each run). Also invoked directly by `TenantProvisioningService.createAdminUser` for a fresh tenant. `seedForCurrentTenant()` is `@Transactional` (called through `ObjectProvider` self-proxy).
- **`SystemAdminBootstrapRunner`** (K-24, `config/`, `ApplicationRunner`, `@Profile("!test")`) — provisions the reserved `system` tenant + admin user at startup via `provisionTenant`. Idempotent (checks subdomain existence). Failures logged + swallowed. RBAC assignment happens in the subsequent `RbacSeeder` startup step.

## Configuration

Config profiles (dev/prod/test) are the single source: [ARCHITECTURE.md - Configuration Profiles](../docs/ARCHITECTURE.md#konfigürasyon-profilleri).

- `application.yaml` (base) + `application-dev.yaml` + `application-prod.yaml` + `application-test.yaml`. Active profile via `SPRING_PROFILES_ACTIVE` (default `dev`).
- `MultiTenancyJpaConfig`: `@EntityScan("com.ibrhalil.forgesys.entity")` + `@EnableJpaRepositories("com.ibrhalil.forgesys.persistence.repository")` + `@EnableJpaAuditing` + Hibernate multi-tenancy beans + `DateTimeProvider` (UTC, [RISK-15](../docs/DECISIONS.md#risk-15)) + `AuditorAware` (SecurityContext userId, fallback `"system"` — [RISK-33](../docs/DECISIONS.md#risk-33)/[RISK-3](../docs/DECISIONS.md#risk-3) ÇÖZÜLDÜ).
- `forgesys.security.password-pepper` — global pepper (K-23); blank fails startup fast. Dev/test ship non-secret defaults; prod must supply a real secret via `PASSWORD_PEPPER`.
- `forgesys.security.app-base-url` — K-21 verification link base (default Vite `http://localhost:3000`; the frontend's `/verify-tenant` page POSTs the token back).
- `forgesys.security.verification-token-ttl-hours` — K-21 token lifetime (default 24).
- `forgesys.security.cors.allowed-origins` — comma-separated; cookie-based auth requires `allowCredentials=true`.
- `forgesys.multi-tenancy.base-domain` — subdomain base for `TenantFilter` host resolution (default `localhost`, i.e. `*.localhost`).
- `forgesys.bootstrap.system-admin.*` (K-24) — reserved system tenant + admin credentials, see `SystemAdminBootstrapProperties`. Defaults in `application-dev.yaml`; prod must override via env. Default password is a placeholder — never deploy to prod with it.

## Gotchas

- **`AuditorAware` reads the authenticated user's id** from SecurityContext (`MultiTenancyJpaConfig`), falling back to `"system"` when there is no authenticated principal — tenant signup, provisioning, and startup runners ([RISK-33](../docs/DECISIONS.md#risk-33), [RISK-3](../docs/DECISIONS.md#risk-3) resolved).
- **`SecurityConfig`** (Epic 2.3 — DONE): `spring-boot-starter-security` is now present. The `SecurityFilterChain` is STATELESS + CSRF-disabled, permits `/api/v1/auth/company/**` + `/api/v1/auth/login` + actuator health/info, authenticates the rest, and wires the JSON `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`. `@EnableMethodSecurity` is active ([K-26](../docs/DECISIONS.md#k-26)) so `@PreAuthorize` is enforced on `iam:*` / `platform:*` endpoints. The password encoder is a `PepperingPasswordEncoder` ([K-23](../docs/DECISIONS.md#k-23)): BCrypt strength 12 (RISK-13) keyed with a global pepper via HMAC-SHA256 pre-hash so a DB leak alone cannot brute-force hashes. The pepper is read from `forgesys.security.password-pepper` / `PASSWORD_PEPPER` env var; a blank value fails startup fast (the `test`/`dev` profiles ship a non-secret default). Legacy pepper-less BCrypt hashes (pre-K-23) still validate and are lazily rehashed to the peppered format (`{sf-peppered}` marker) on the next successful login (`AuthService.upgradeHashIfNeeded`).
- **`TenantFilter` runs before the security chain** via a `FilterRegistrationBean` at order `-101` (security is at `-100`) in `SecurityConfig`, so tenant context is resolved before the JWT auth filter executes. The registration also suppresses the bare `@Component` filter's default low-precedence auto-registration.
- **`RequestMetadataFilter`** (K-19) runs BEFORE the tenant filter at order `-102` (tenant `-101`, security `-100`) via a `FilterRegistrationBean`, capturing `X-Request-Id` (or a generated UUID), client IP (`X-Forwarded-For` first hop / `X-Real-IP` / `getRemoteAddr`) and User-Agent into `RequestContext` + MDC `traceId`. Both are cleared in `finally` (no ThreadLocal leak). All requests go through it (incl. actuator).
- **REQUIRES_NEW audit writes + `@Transactional` tests:** `AuditService` / `LoginHistoryService` run `REQUIRES_NEW`, so during `@SpringBootTest` controller tests their writes commit outside the test's rollback and pollute the shared cached-context H2. Tests asserting on `t_audit_logs` / `t_login_history` must use membership (`hasItem`) + unique sentinels, NOT positional / count assertions (see `AuditControllerTest`).
- CORS is configured in `CorsConfig` (`CorsConfigurationSource`, `allowCredentials=true`, origins via `forgesys.security.cors.allowed-origins`, default Vite `http://localhost:5173` + `http://localhost:3000`). Required because auth is cookie-based.
- **`PlatformCompanyService.executeWithoutTenantContext`** (K-25) — temporarily clears `TenantContext` to query the `public` schema. This is the **only sanctioned** cross-tenant read path; do not replicate the pattern elsewhere. [RISK-18](../docs/DECISIONS.md#risk-18): `platform:*` permissions are currently seeded into every tenant's Admin role — narrowing this to the system tenant only is open.
- **Spring Boot 4.1 / Spring Security 7 gotchas:** test slice annotations (`@WebMvcTest`, `@AutoConfigureMockMvc`, `@DataJpaTest`) were REMOVED from the standard autoconfigure — build MockMvc via `MockMvcBuilders.webAppContextSetup(wac).addFilters(securityFilter)` or use `@SpringBootTest` + a real port. Jackson is v3: `ObjectMapper`/databind moved to package `tools.jackson.*` (annotations stayed at `com.fasterxml.jackson.annotation`). `SecurityProperties` moved to `org.springframework.boot.security.autoconfigure` and lost `DEFAULT_FILTER_ORDER` (use literal `-100`).
- The `CompanyStatus` enum: `PROVISIONING`, `ACTIVE`, `SUSPENDED`, `TERMINATED`. K-21 activates `PROVISIONING` (phase 1 sets it; phase 2 `verifyAndProvision` promotes to `ACTIVE`). `PlatformCompanyService.updateStatus` can flip to `SUSPENDED`/`TERMINATED` manually; full lifecycle (billing-driven) arrives in Faz 6.

## Forward-looking infrastructure (partially implemented — Epic 2.10)

K-19 core (3-layer log) is IMPLEMENTED; the K-27 security-hardening extensions and the request/trace **table** remain. Detailed rationale in [DECISIONS.md](../docs/DECISIONS.md) (K-19 / K-27 / K-28 / K-29 / K-30) and scheduling in [ROADMAP.md Epic 2.10](../docs/ROADMAP.md). As pieces ship they move into the relevant sections above (see the K-19 IMPLEMENTED note under Endpoint rules).

- **K-19 (DONE):** `AuditService` + `LoginHistoryService` + `AuditQueryService` + `RequestMetadataFilter` / `RequestContext`. Writes: every admin action → `t_audit_logs` (actor/action/entity/ip/traceId); every login attempt (success + failure) → `t_login_history`. Read: `GET /audit-logs`, `GET /login-history` (paged, filtered, `iam:audit:read`). NOT done: request/trace log **table** (`GET /request-logs`), and the K-27 items below.
- **K-27 audit/log hardening (remaining):** old/new value capture + high-risk request body (`request_body` JSONB, mask-first: `password`/`token`/`secret` → `[REDACTED]`) into `t_audit_logs`; config-driven high-risk paths (`forgesys.audit.high-risk-paths`). `@AuditLog` AOP aspect delegating to `AuditService` (replaces explicit calls — AOP infra already on classpath). `@ApprovalRequired` (or explicit service call) → `t_pending_actions` two-admin approval for user/role delete. Anomaly detection passive (rate-limit + unusual-pattern → K-29 alert, not block).
- **K-28 session management** — depends on Epic 2.5 (refresh tokens) + Epic 2.6 (Redis). Active sessions live in Redis (`session:{userId}:{sessionId}` → device/ip/user_agent/loginAt/lastSeen, TTL = refresh-token lifetime). `t_sessions_log` (tenant) records LOGIN/LOGOUT/SESSION_REVOKED/EXPIRED events (permanent audit). Endpoints `/api/v1/users/me/sessions` (self) + `/api/v1/users/{id}/sessions` (admin, `iam:user:write`) + `DELETE .../sessions/{sessionId}` (remote revoke — Redis key delete + `TokenBlacklistService`).
- **K-29 notification subsystem** — `NotificationService.send(userId, type, payload)`, two channels: in-app (`t_notifications`, polling; WebSocket Faz 5+) + mail (`MailNotificationSender` Faz 5; `LogNotificationSender` dev; `InMemoryNotificationSender` test — same mail infra as K-21 `VerificationSender`). Type catalog: `SUSPICIOUS_LOGIN`, `NEW_DEVICE_LOGIN`, `FAILED_LOGIN_SPIKE`, `PASSWORD_CHANGED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`, `BULK_DELETE_ALERT`, `APPROVAL_REQUESTED`, `APPROVAL_DECISION`, `SESSION_REVOKED_BY_ADMIN`. Templates in `infra/templates/` (TR/EN). Per-user preferences in `t_notification_preferences`.
- **K-30 activity feed** — user-facing materialized view on top of `t_audit_logs`. Activity text generated from `{action}_{entity}` template map (i18n): "Ali 'Tasarım Ekibi' grubunu oluşturdu". Visibility scope (public/team/private). `/api/v1/activities` (paged, filtered). UI arrives in Faz 4 (admin panel — K-20).

> **Implementation note:** admin/RBAC write endpoints already emit audit entries via explicit `auditService.record(...)` calls (K-19). When adding new admin endpoints, add an `auditService.record(...)` call (or, once K-27 lands, `@AuditLog`). `@ApprovalRequired` (K-27) gates high-risk state mutations (user/role delete). High-risk body logging config (`forgesys.audit.high-risk-paths`) lists which paths log body. Notification triggers fire from the service layer, not controllers — keep the service the single source of side effects.
