# backend/AGENTS.md

## Module

Spring Boot application — controller/service/security/config. Depends on `common` + `persistence`. Only this module produces an executable jar (`systemforge-backend.jar`). General rules from the root AGENTS.md apply.

For commands see [README](../README.md#build-komutları). Backend summary: `./mvnw -pl backend spring-boot:run` (NOT from root, always `-pl backend`), `./mvnw -pl backend test -Dtest=ClassName#method`.

## Package layout

Root package `com.ibrhalil.systemforge` (NOT a `.backend` subpackage):
- `SystemforgeApplication` — main.
- `tenant/` — `TenantFilter` (subdomain resolution).
- `controller/` — REST (`/api/v1/*`). `[PHASE 3]` `modules/` subpackage.
- `service/` — business logic incl. `TenantProvisioningService`, `TenantMigrationSupport` (shared programmatic tenant Flyway). `[PHASE 3]` `modules/` subpackage.
- `dto/` — request/response DTOs (`record`).
- `exception/` — `GlobalExceptionHandler`, uniform error shape (`ApiErrorResponse`/`ApiFieldError`/`ApiErrorFactory`), `ErrorCode` (stable wire codes), `BusinessException` -> `AuthException`/`ResourceNotFoundException` hierarchy.
- `security/` — Spring Security adapters: `RestAuthenticationEntryPoint` (401), `RestAccessDeniedHandler` (403). JWT/UserDetails (Chunk C) land here too.
- `config/` — `MultiTenancyJpaConfig`, `SecurityConfig` (filter chain + BCrypt + tenant-filter ordering), `CorsConfig`, `TenantMigrationRunner` (`ApplicationRunner`, `@Profile("!test")`).

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
- Errors use the uniform `ApiErrorResponse` (`GlobalExceptionHandler`): every error path returns `{timestamp, status, error, code, message, path, traceId, fields[]}`. `code` is a stable `ErrorCode` (lowercased enum name, e.g. `auth_bad_credentials`) — clients branch on it; message/status may evolve. Sensitive rejected values (`password`/`token`/`secret`/`credential`) are masked to `[REDACTED]` in field errors. `BusinessException` (`AuthException`/`ResourceNotFoundException`) carries an `ErrorCode`; `TenantNotFoundException` stays a plain `RuntimeException` in `common` and is translated by the handler. `traceId` comes from MDC (`RequestLoggingFilter` sets it in a later chunk; generated per-error until then).
- Bean Validation (`@Valid` + `@NotBlank`/`@Pattern`/`@Email`).

### Current endpoints

| Method | Path | Description | Auth |
|--------|------|----------|------|
| `POST` | `/api/v1/auth/company/register` | New tenant signup — SYNCHRONOUS: `provisionTenant` creates an `ACTIVE` Company + schema CREATE + Flyway tenant migration + admin user. | Public |
| `POST` | `/api/v1/auth/login` | Email+password -> RS256 access token. Token is set as an httpOnly cookie (`sf_access_token`) AND returned in the body. Tenant resolved by subdomain. Bad credentials/unknown user both -> `401 auth_bad_credentials` (no enumeration oracle). | Public |
| `GET` | `/api/v1/auth/me` | Current user (id/email/tenant/authorities) from the JWT cookie — no DB hit (principal rebuilt from claims). | Authenticated |

> **Auth stack (Faz 2.3/2.4 + 2.5 minimal — DONE):** RS256 JWT via `spring-boot-starter-oauth2-resource-server`. `JwtTokenProvider` mints tokens (sub=userId, claims: email/tenant/authorities). `JwtAuthenticationFilter` reads the cookie, decodes, rebuilds `CustomUserDetails` from claims, sets `SecurityContext` (no DB per request). `CustomUserDetailsService` (login only) resolves authorities = direct roles + active group roles -> permissions. The oauth2 auto-config filter is NOT enabled (custom filter — [RISK-14](../docs/DECISIONS.md#risk-14)). RSA keys: configured PEMs in prod, **ephemeral** in dev/test (warning logged) — never commit `certs/*.pem`.

> **Deferred to next session (Epic 2.5 rest / 2.6 / 2.9):** refresh tokens + logout (Redis blacklist), register (email-domain check), token revocation (`tokenInvalidBefore` check in the filter — column exists), brute-force lockout, `@PreAuthorize` RBAC enforcement, User/Role/Permission/Group CRUD.

> **A two-phase signup flow is planned (K-21) but NOT implemented:** create a `PROVISIONING` Company, then promote to `ACTIVE` on email verify. Details in [DECISIONS.md K-21](../docs/DECISIONS.md#k-21). It is parked in the ROADMAP as Epic 2.0.C; when implemented this section is updated.

## Service layer

- **`TenantProvisioningService.provisionTenant(request)`** — single-phase SYNCHRONOUS flow:
  1. `validateUnique` (subdomain + emailDomain uniqueness)
  2. `createSchema` (raw JDBC `CREATE SCHEMA`)
  3. `runTenantMigrations` -> delegates to `TenantMigrationSupport.migrateSchema(schemaName)` (shared programmatic Flyway)
  4. `createCompany` (status=`ACTIVE`)
  5. `createAdminUser` (tenant context set + finally clear)
- DDL (`CREATE SCHEMA`) is an implicit commit in PostgreSQL -> OUTSIDE the transaction. **`provisionTenant` is currently NOT `@Transactional`** — partial-write risk ([DEBT-10](../docs/DECISIONS.md#debt-10)). It is refactored when K-21 lands (`createPendingCompany` + `verifyAndProvision`, both transactional).
- Lookups should use `@Transactional(readOnly=true)`.
- **`TenantMigrationRunner`** (`config/`, `ApplicationRunner`, `@Profile("!test")`) — at startup, iterates `t_companies` (public schema, no tenant context) and runs `TenantMigrationSupport.migrateSchema(schemaName)` per tenant. Applies new tenant migrations (`tenant/V2`, `V3`, ...) to EXISTING tenants ([RISK-16](../docs/DECISIONS.md#risk-16) — RESOLVED). Per-tenant try/catch: one broken schema doesn't abort others. Disabled in `test` profile (H2 + flyway off).

## Configuration

Config profiles (dev/prod/test) are the single source: [ARCHITECTURE.md - Configuration Profiles](../docs/ARCHITECTURE.md#konfigürasyon-profilleri).

- `application.yaml` (base) + `application-dev.yaml` + `application-prod.yaml` + `application-test.yaml`. Active profile via `SPRING_PROFILES_ACTIVE` (default `dev`).
- `MultiTenancyJpaConfig`: `@EntityScan("com.ibrhalil.systemforge.entity")` + `@EnableJpaRepositories("com.ibrhalil.systemforge.persistence.repository")` + `@EnableJpaAuditing` + Hibernate multi-tenancy beans + `DateTimeProvider` (UTC, [RISK-15](../docs/DECISIONS.md#risk-15)) + `AuditorAware` (hardcoded `"system"`).

## Gotchas

- **`AuditorAware` is hardcoded to `"system"`** ([RISK-3](../docs/DECISIONS.md#risk-3)) — once auth lands it must read the real userId from SecurityContext. Signup endpoints are always audited as `"system"` (no authenticated user in the tenant-signup context) — this is expected, not a bug.
- **`SecurityConfig`** (Epic 2.3 — DONE): `spring-boot-starter-security` is now present. The `SecurityFilterChain` is STATELESS + CSRF-disabled, permits `/api/v1/auth/**` + actuator health/info, authenticates the rest, and wires the JSON `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`. The password encoder is a `PepperingPasswordEncoder` ([K-23](../docs/DECISIONS.md#k-23)): BCrypt strength 12 (RISK-13) keyed with a global pepper via HMAC-SHA256 pre-hash so a DB leak alone cannot brute-force hashes. The pepper is read from `systemforge.security.password-pepper` / `PASSWORD_PEPPER` env var; a blank value fails startup fast (the `test`/`dev` profiles ship a non-secret default). Legacy pepper-less BCrypt hashes (pre-K-23) still validate and are lazily rehashed to the peppered format (`{sf-peppered}` marker) on the next successful login (`AuthService.upgradeHashIfNeeded`). **Until the JWT filter lands (Chunk C) there is no way to authenticate, so every non-`auth/**` request is 401** — and Spring Boot still auto-creates the default `user` (logged password); it is removed once `CustomUserDetailsService` exists.
- **`TenantFilter` runs before the security chain** via a `FilterRegistrationBean` at order `-101` (security is at `-100`) in `SecurityConfig`, so tenant context is resolved before the JWT auth filter executes. The registration also suppresses the bare `@Component` filter's default low-precedence auto-registration.
- CORS is configured in `CorsConfig` (`CorsConfigurationSource`, `allowCredentials=true`, origins via `systemforge.security.cors.allowed-origins`, default Vite `http://localhost:5173`). Required because auth is cookie-based.
- **Spring Boot 4.1 / Spring Security 7 gotchas:** test slice annotations (`@WebMvcTest`, `@AutoConfigureMockMvc`, `@DataJpaTest`) were REMOVED from the standard autoconfigure — build MockMvc via `MockMvcBuilders.webAppContextSetup(wac).addFilters(securityFilter)` or use `@SpringBootTest` + a real port. Jackson is v3: `ObjectMapper`/databind moved to package `tools.jackson.*` (annotations stayed at `com.fasterxml.jackson.annotation`). `SecurityProperties` moved to `org.springframework.boot.security.autoconfigure` and lost `DEFAULT_FILTER_ORDER` (use literal `-100`).
- The `CompanyStatus` enum: `PROVISIONING`, `ACTIVE`, `SUSPENDED`, `TERMINATED`. **Current code only uses `ACTIVE`** (`provisionTenant` sets ACTIVE directly). `PROVISIONING` activates when K-21 (Epic 2.0.C) is implemented.
