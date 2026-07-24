# backend/AGENTS.md

## Module

Spring Boot application — controller/service/security/config. Depends on `common` + `persistence`. Only this module produces an executable jar (`forgesys-backend.jar`). General rules from the root AGENTS.md apply.

For commands see [README](../README.md#build-komutları). Backend summary: `./mvnw -pl backend spring-boot:run` (NOT from root, always `-pl backend`), `./mvnw -pl backend test -Dtest=ClassName#method`.

## Package layout

Root package `com.ibrhalil.forgesys` (NOT a `.backend` subpackage):
- `ForgeSysApplication` — main.
- `tenant/` — `TenantFilter` (subdomain resolution).
- `controller/` — REST (`/api/v1/*`). `[PHASE 3]` `modules/` subpackage.
- `service/` — business logic: `TenantProvisioningService`, `TenantMigrationSupport` (shared programmatic tenant Flyway), `AuthService`, `UserService`/`RoleService`/`GroupService`/`PermissionService` (tenant-scoped RBAC), `PlatformCompanyService` (cross-tenant). `[PHASE 3]` `modules/` subpackage.
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
- Errors use the uniform `ApiErrorResponse` (`GlobalExceptionHandler`): every error path returns `{timestamp, status, error, code, message, path, traceId, fields[]}`. `code` is a stable `ErrorCode` (lowercased enum name, e.g. `auth_bad_credentials`) — clients branch on it; message/status may evolve. Sensitive rejected values (`password`/`token`/`secret`/`credential`) are masked to `[REDACTED]` in field errors. `BusinessException` (`AuthException`/`ResourceNotFoundException`) carries an `ErrorCode`; `TenantNotFoundException` stays a plain `RuntimeException` in `common` and is translated by the handler. `traceId` comes from MDC (`RequestLoggingFilter` sets it in a later chunk; generated per-error until then). Client-error mappings (Faz D): malformed path/query params (`MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `jakarta.validation.ConstraintViolationException`) → 400 `validation_error`; concurrent uniqueness races (`DataIntegrityViolationException`) → 400 with the precise `*_TAKEN` code (constraint-name substring map: `users_email`→`user_email_taken` etc.) or `business_error` fallback — never 500.
- Bean Validation (`@Valid` + `@NotBlank`/`@Pattern`/`@Email`).

### Current endpoints

> RBAC enforcement via `@EnableMethodSecurity` (`SecurityConfig`) + `@PreAuthorize` (K-26). Permission namespace `{module}:{resource}:{action}` — see `PermissionCatalog` for the seeded catalog (iam:* + platform:*) and `RbacSeeder` for the seeding logic.

**Public (no auth, `SecurityConfig.permitAll`):**

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/auth/company/register` | K-21 phase 1 — creates a `PROVISIONING` Company + `TenantVerificationToken` and emails the verification link. 202 Accepted (tenant not ready yet). NO schema/Flyway/admin user. |
| `POST` | `/api/v1/auth/company/verify` | K-21 phase 2 — consumes the token, runs `CREATE SCHEMA` + Flyway tenant migration + admin user, promotes Company to `ACTIVE`. 200 OK. |
| `POST` | `/api/v1/auth/company/suggest-subdomain` | K-21 — slug candidates (Turkish-aware) for an org name; up to 3 unique suggestions. |
| `POST` | `/api/v1/auth/login` | Email+password → RS256 access token. Cookie (`sf_access_token`) + body. Tenant resolved by subdomain. Unknown/bad-password both → `401 auth_bad_credentials` (no enumeration). |
| `GET` | `/actuator/health/**`, `/actuator/info` | Health/info (prod exposes only health). |

**Authenticated self-service (any logged-in user, no `iam:*` permission):**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/auth/me` | Current user (id/email/tenant/authorities) from the JWT cookie — no DB hit. |
| `POST` | `/api/v1/auth/logout` | Expires the `sf_access_token` cookie. (Redis blacklist deferred — Epic 2.6.) |
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

> **Auth stack (Faz 2.3/2.4/2.5/2.9 — DONE):** RS256 JWT via `spring-boot-starter-oauth2-resource-server`. `JwtTokenProvider` mints tokens (sub=userId, claims: email/tenant/authorities). `JwtAuthenticationFilter` reads the cookie, decodes, rebuilds `CustomUserDetails` from claims, sets `SecurityContext` (no DB per request). `CustomUserDetailsService` (login only) resolves authorities = direct roles + active group roles → permissions. The oauth2 auto-config filter is NOT enabled (custom filter — [RISK-14](../docs/DECISIONS.md#risk-14)). RSA keys: configured PEMs in prod, **ephemeral** in dev/test (warning logged) — never commit `certs/*.pem`. Method security is enabled (`@EnableMethodSecurity` + `@PreAuthorize`, [K-26](../docs/DECISIONS.md#k-26)); all `iam:*` / `platform:*` endpoints are guarded. Self-service `/users/me/**` is authenticated-only (no `iam:*` permission).

> **Deferred (Epic 2.5 rest / 2.6):** refresh tokens + logout (Redis blacklist), token revocation (`tokenInvalidBefore` check in the filter — column exists, [RISK-21] open), tenant içi user email doğrulama + password reset akış (entity field'ları hazır: `emailVerificationToken`/`passwordResetToken`). **Brute-force lockout ([RISK-22]) DONE (login-scoped):** `AuthService.login` counts `failedLoginAttempts`/`lockedUntil` (5/15dk → `auth_account_locked` 423); filter-side revocation (locked account's existing token) arrives with [RISK-21].

> **Two-phase signup flow (K-21) — IMPLEMENTED:** `POST /api/v1/auth/company/register` returns 202 with a `PROVISIONING` Company + issues a `TenantVerificationToken` (admin credentials pre-hashed into the token). `POST /api/v1/auth/company/verify` consumes the token, runs `CREATE SCHEMA` + programmatic Flyway + admin user, and promotes the Company to `ACTIVE`. `TenantProvisioningService` is split into `createPendingCompany` + `verifyAndProvision` (both `@Transactional`; DDL inside `verifyAndProvision` is an implicit commit, see [DEBT-10](../docs/DECISIONS.md#debt-10) partial). `provisionSystemTenant` is the bootstrap auto-verify path (no mail). Details in [DECISIONS.md K-21](../docs/DECISIONS.md#k-21) + [K-32](../docs/DECISIONS.md#k-32) (email_domain dropped, 1:N org domains).

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
- **`AuthService.login`** — read-write `@Transactional` (lazy pepper rehash, [K-23](../docs/DECISIONS.md#k-23)).
- **`TenantMigrationRunner`** (`config/`, `ApplicationRunner`, `@Profile("!test")`) — at startup, iterates `t_companies` (public schema, no tenant context) and runs `TenantMigrationSupport.migrateSchema(schemaName)` per tenant. Applies new tenant migrations (`tenant/V2`, `V3`, ...) to EXISTING tenants ([RISK-16](../docs/DECISIONS.md#risk-16) — RESOLVED). Per-tenant try/catch: one broken schema doesn't abort others. Disabled in `test` profile (H2 + flyway off).
- **`RbacSeeder`** (`config/`, `ApplicationRunner`, `@Profile("!test")`) — at startup, iterates `t_companies` and per tenant (via `TenantContext` switch) ensures the full `PermissionCatalog` + an `Admin` role carrying every permission + assigns `Admin` to any role-less user. Idempotent (re-syncs Admin permissions on each run). Also invoked directly by `TenantProvisioningService.createAdminUser` for a fresh tenant. `seedForCurrentTenant()` is `@Transactional` (called through `ObjectProvider` self-proxy).
- **`SystemAdminBootstrapRunner`** (K-24, `config/`, `ApplicationRunner`, `@Profile("!test")`) — provisions the reserved `system` tenant + admin user at startup via `provisionTenant`. Idempotent (checks subdomain existence). Failures logged + swallowed. RBAC assignment happens in the subsequent `RbacSeeder` startup step.

## Configuration

Config profiles (dev/prod/test) are the single source: [ARCHITECTURE.md - Configuration Profiles](../docs/ARCHITECTURE.md#konfigürasyon-profilleri).

- `application.yaml` (base) + `application-dev.yaml` + `application-prod.yaml` + `application-test.yaml`. Active profile via `SPRING_PROFILES_ACTIVE` (default `dev`).
- `MultiTenancyJpaConfig`: `@EntityScan("com.ibrhalil.forgesys.entity")` + `@EnableJpaRepositories("com.ibrhalil.forgesys.persistence.repository")` + `@EnableJpaAuditing` + Hibernate multi-tenancy beans + `DateTimeProvider` (UTC, [RISK-15](../docs/DECISIONS.md#risk-15)) + `AuditorAware` (hardcoded `"system"` — [RISK-3](../docs/DECISIONS.md#risk-3)).
- `forgesys.security.password-pepper` — global pepper (K-23); blank fails startup fast. Dev/test ship non-secret defaults; prod must supply a real secret via `PASSWORD_PEPPER`.
- `forgesys.security.app-base-url` — K-21 verification link base (default Vite `http://localhost:3000`; the frontend's `/verify-tenant` page POSTs the token back).
- `forgesys.security.verification-token-ttl-hours` — K-21 token lifetime (default 24).
- `forgesys.security.cors.allowed-origins` — comma-separated; cookie-based auth requires `allowCredentials=true`.
- `forgesys.multi-tenancy.base-domain` — subdomain base for `TenantFilter` host resolution (default `localhost`, i.e. `*.localhost`).
- `forgesys.bootstrap.system-admin.*` (K-24) — reserved system tenant + admin credentials, see `SystemAdminBootstrapProperties`. Defaults in `application-dev.yaml`; prod must override via env. Default password is a placeholder — never deploy to prod with it.

## Gotchas

- **`AuditorAware` is hardcoded to `"system"`** ([RISK-3](../docs/DECISIONS.md#risk-3)) — once auth lands it must read the real userId from SecurityContext. Signup endpoints are always audited as `"system"` (no authenticated user in the tenant-signup context) — this is expected, not a bug.
- **`SecurityConfig`** (Epic 2.3 — DONE): `spring-boot-starter-security` is now present. The `SecurityFilterChain` is STATELESS + CSRF-disabled, permits `/api/v1/auth/company/**` + `/api/v1/auth/login` + actuator health/info, authenticates the rest, and wires the JSON `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`. `@EnableMethodSecurity` is active ([K-26](../docs/DECISIONS.md#k-26)) so `@PreAuthorize` is enforced on `iam:*` / `platform:*` endpoints. The password encoder is a `PepperingPasswordEncoder` ([K-23](../docs/DECISIONS.md#k-23)): BCrypt strength 12 (RISK-13) keyed with a global pepper via HMAC-SHA256 pre-hash so a DB leak alone cannot brute-force hashes. The pepper is read from `forgesys.security.password-pepper` / `PASSWORD_PEPPER` env var; a blank value fails startup fast (the `test`/`dev` profiles ship a non-secret default). Legacy pepper-less BCrypt hashes (pre-K-23) still validate and are lazily rehashed to the peppered format (`{sf-peppered}` marker) on the next successful login (`AuthService.upgradeHashIfNeeded`).
- **`TenantFilter` runs before the security chain** via a `FilterRegistrationBean` at order `-101` (security is at `-100`) in `SecurityConfig`, so tenant context is resolved before the JWT auth filter executes. The registration also suppresses the bare `@Component` filter's default low-precedence auto-registration.
- CORS is configured in `CorsConfig` (`CorsConfigurationSource`, `allowCredentials=true`, origins via `forgesys.security.cors.allowed-origins`, default Vite `http://localhost:5173` + `http://localhost:3000`). Required because auth is cookie-based.
- **`PlatformCompanyService.executeWithoutTenantContext`** (K-25) — temporarily clears `TenantContext` to query the `public` schema. This is the **only sanctioned** cross-tenant read path; do not replicate the pattern elsewhere. [RISK-18](../docs/DECISIONS.md#risk-18): `platform:*` permissions are currently seeded into every tenant's Admin role — narrowing this to the system tenant only is open.
- **Spring Boot 4.1 / Spring Security 7 gotchas:** test slice annotations (`@WebMvcTest`, `@AutoConfigureMockMvc`, `@DataJpaTest`) were REMOVED from the standard autoconfigure — build MockMvc via `MockMvcBuilders.webAppContextSetup(wac).addFilters(securityFilter)` or use `@SpringBootTest` + a real port. Jackson is v3: `ObjectMapper`/databind moved to package `tools.jackson.*` (annotations stayed at `com.fasterxml.jackson.annotation`). `SecurityProperties` moved to `org.springframework.boot.security.autoconfigure` and lost `DEFAULT_FILTER_ORDER` (use literal `-100`).
- The `CompanyStatus` enum: `PROVISIONING`, `ACTIVE`, `SUSPENDED`, `TERMINATED`. K-21 activates `PROVISIONING` (phase 1 sets it; phase 2 `verifyAndProvision` promotes to `ACTIVE`). `PlatformCompanyService.updateStatus` can flip to `SUSPENDED`/`TERMINATED` manually; full lifecycle (billing-driven) arrives in Faz 6.

## Forward-looking infrastructure (NOT implemented — Epic 2.10 scope)

These are planned subsystems that future code must integrate with. Detailed rationale in [DECISIONS.md](../docs/DECISIONS.md) (K-27 / K-28 / K-29 / K-30) and scheduling in [ROADMAP.md Epic 2.10](../docs/ROADMAP.md). When implementing, this section moves into the relevant sections above.

- **K-19 + K-27 audit/log** — `AuditService` + AOP `@AuditLog` writes `t_audit_logs` (actor/action/entity/old-new JSONB/request_body JSONB for high-risk/ip/trace_id) + `t_login_history` (user/success/reason-enum/ip/user_agent). Failed login attempts ARE logged (not just successful). High-risk endpoint request bodies (create/delete/admin) are logged mask-first (`password`/`token`/`secret` → `[REDACTED]`). `@ApprovalRequired` (or explicit service call) routes user/role delete through `t_pending_actions` (two-admin approval). Anomaly detection is passive (rate-limit + unusual-pattern → K-29 alert, not block).
- **K-28 session management** — depends on Epic 2.5 (refresh tokens) + Epic 2.6 (Redis). Active sessions live in Redis (`session:{userId}:{sessionId}` → device/ip/user_agent/loginAt/lastSeen, TTL = refresh-token lifetime). `t_sessions_log` (tenant) records LOGIN/LOGOUT/SESSION_REVOKED/EXPIRED events (permanent audit). Endpoints `/api/v1/users/me/sessions` (self) + `/api/v1/users/{id}/sessions` (admin, `iam:user:write`) + `DELETE .../sessions/{sessionId}` (remote revoke — Redis key delete + `TokenBlacklistService`).
- **K-29 notification subsystem** — `NotificationService.send(userId, type, payload)`, two channels: in-app (`t_notifications`, polling; WebSocket Faz 5+) + mail (`MailNotificationSender` Faz 5; `LogNotificationSender` dev; `InMemoryNotificationSender` test — same mail infra as K-21 `VerificationSender`). Type catalog: `SUSPICIOUS_LOGIN`, `NEW_DEVICE_LOGIN`, `FAILED_LOGIN_SPIKE`, `PASSWORD_CHANGED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`, `BULK_DELETE_ALERT`, `APPROVAL_REQUESTED`, `APPROVAL_DECISION`, `SESSION_REVOKED_BY_ADMIN`. Templates in `infra/templates/` (TR/EN). Per-user preferences in `t_notification_preferences`.
- **K-30 activity feed** — user-facing materialized view on top of `t_audit_logs`. Activity text generated from `{action}_{entity}` template map (i18n): "Ali 'Tasarım Ekibi' grubunu oluşturdu". Visibility scope (public/team/private). `/api/v1/activities` (paged, filtered). UI arrives in Faz 4 (admin panel — K-20).

> **Implementation note:** every new admin/RBAC endpoint should anticipate `@AuditLog` (K-19) and `@ApprovalRequired` (K-27, if it mutates high-risk state) annotations. High-risk body logging config (`forgesys.audit.high-risk-paths`) lists which paths log body. Notification triggers fire from the service layer, not controllers — keep the service the single source of side effects.
