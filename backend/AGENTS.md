# backend/AGENTS.md

## Module

Spring Boot application — controller/service/security/config. Depends on `common` + `persistence`. Only this module produces an executable jar (`systemforge-backend.jar`). General rules from the root AGENTS.md apply.

For commands see [README](../README.md#build-komutları). Backend summary: `./mvnw -pl backend spring-boot:run` (NOT from root, always `-pl backend`), `./mvnw -pl backend test -Dtest=ClassName#method`.

## Package layout

Root package `com.ibrhalil.systemforge` (NOT a `.backend` subpackage):
- `SystemforgeApplication` — main.
- `tenant/` — `TenantFilter` (subdomain resolution).
- `controller/` — REST (`/api/v1/*`). `[PHASE 3]` `modules/` subpackage.
- `service/` — business logic. `[PHASE 3]` `modules/` subpackage.
- `dto/` — request/response DTOs (`record`).
- `exception/` — `GlobalExceptionHandler`, `ErrorResponse`.
- `config/` — `MultiTenancyJpaConfig`, `SecurityConfig`.

## Tenant Context rules (CRITICAL)

- `TenantFilter` (`OncePerRequestFilter`): Host header -> subdomain -> `CompanyRepository.findBySubdomain` -> `schemaName` -> `TenantContext`. Only `ACTIVE` Companies are resolved (`PROVISIONING`/`SUSPENDED`/`TERMINATED` are not).
- `shouldNotFilter()` exempts `/api/v1/auth/**` and `/actuator/**` (no tenant context needed). `/api/v1/auth/company/register` MUST be exempt — the tenant is being created.
- **Do NOT validate tenant in the controller** — the filter is the single responsibility owner.
- When tenant context is null the resolver returns `"public"` -> public-schema data (Company) is reachable.
- Services that programmatically set the tenant (e.g. `TenantProvisioningService.provisionTenant` -> `createAdminUser`) MUST call `TenantContext.clear()` in `finally`.
- The `X-Tenant-ID` header fallback is active **only in the `dev` profile**. Fully disabled in prod.
- `TenantContext` is NOT propagated automatically across `@Async` threads — a `TaskDecorator` is required ([RISK-10](../docs/DECISIONS.md#risk-10)).

## Endpoint rules

- All endpoints live under the `/api/v1/*` prefix.
- Return a response DTO (`record`); entities MUST NOT be exposed directly. **Current exception:** `AuthController.registerCompany()` returns `ResponseEntity<Map<String,Object>>` — MapStruct + DTO refactor happens in Epic 2.1 ([ROADMAP](../docs/ROADMAP.md)).
- Errors use the uniform `ErrorResponse` (`GlobalExceptionHandler`): `TenantNotFoundException`/`IllegalArgumentException`/validation -> 400, generic -> 500.
- Bean Validation (`@Valid` + `@NotBlank`/`@Pattern`/`@Email`).

### Current endpoint

| Method | Path | Description | Auth |
|--------|------|----------|------|
| `POST` | `/api/v1/auth/company/register` | New tenant signup — SYNCHRONOUS: `provisionTenant` creates an `ACTIVE` Company + schema CREATE + Flyway tenant migration + admin user. | Public |

> **A two-phase flow is planned (K-21) but NOT implemented:** create a `PROVISIONING` Company, then promote to `ACTIVE` on email verify. Details in [DECISIONS.md K-21](../docs/DECISIONS.md#k-21). It is parked in the ROADMAP as Epic 2.0.C; when implemented this section is updated.

> `TenantFilter` exemption: the endpoint is under the `/api/v1/auth/**` prefix, so it is already covered by `shouldNotFilter`.

## Service layer

- **`TenantProvisioningService.provisionTenant(request)`** — single-phase SYNCHRONOUS flow:
  1. `validateUnique` (subdomain + emailDomain uniqueness)
  2. `createSchema` (raw JDBC `CREATE SCHEMA`)
  3. `runTenantMigrations` (programmatic Flyway)
  4. `createCompany` (status=`ACTIVE`)
  5. `createAdminUser` (tenant context set + finally clear)
- DDL (`CREATE SCHEMA`) is an implicit commit in PostgreSQL -> OUTSIDE the transaction. **`provisionTenant` is currently NOT `@Transactional`** — partial-write risk ([DEBT-10](../docs/DECISIONS.md#debt-10)). It is refactored when K-21 lands (`createPendingCompany` + `verifyAndProvision`, both transactional).
- Lookups should use `@Transactional(readOnly=true)`.

## Configuration

Config profiles (dev/prod/test) are the single source: [ARCHITECTURE.md - Configuration Profiles](../docs/ARCHITECTURE.md#konfigürasyon-profilleri).

- `application.yaml` (base) + `application-dev.yaml` + `application-prod.yaml` + `application-test.yaml`. Active profile via `SPRING_PROFILES_ACTIVE` (default `dev`).
- `MultiTenancyJpaConfig`: `@EntityScan("com.ibrhalil.systemforge.entity")` + `@EnableJpaRepositories("com.ibrhalil.systemforge.persistence.repository")` + `@EnableJpaAuditing` + Hibernate multi-tenancy beans + `DateTimeProvider` (UTC, [RISK-15](../docs/DECISIONS.md#risk-15)) + `AuditorAware` (hardcoded `"system"`).

## Gotchas

- **`AuditorAware` is hardcoded to `"system"`** ([RISK-3](../docs/DECISIONS.md#risk-3)) — once auth lands it must read the real userId from SecurityContext. Signup endpoints are always audited as `"system"` (no authenticated user in the tenant-signup context) — this is expected, not a bug.
- **`SecurityConfig`** currently defines only a `BCryptPasswordEncoder` bean (strength 10); there is NO full `SecurityFilterChain` yet. **Important:** the `spring-boot-starter-security` dependency is also ABSENT — only `spring-security-crypto` (for BCrypt). So a SecurityFilterChain cannot be set up at all; the starter is added together with the setup in a single PR in Phase 2.3 ([ROADMAP Epic 2.3](../docs/ROADMAP.md)). The signup endpoint stays open because it is exempt from `TenantFilter`.
- BCrypt strength is 10 (target 12 — [RISK-13](../docs/DECISIONS.md#risk-13)).
- CORS is not present yet (Phase 2.3). The Vite proxy hides it in dev; it breaks in prod.
- The `CompanyStatus` enum: `PROVISIONING`, `ACTIVE`, `SUSPENDED`, `TERMINATED`. **Current code only uses `ACTIVE`** (`provisionTenant` sets ACTIVE directly). `PROVISIONING` activates when K-21 (Epic 2.0.C) is implemented.
