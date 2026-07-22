# persistence/AGENTS.md

## Module

JPA entities, repositories, multi-tenancy infrastructure, Flyway migration. Depends on `common`. NO Spring Web/Security dependency. General rules from the root AGENTS.md apply.

For commands see [README](../README.md#build-komutları). Module summary: `./mvnw -pl persistence test`, `./mvnw -pl persistence -am clean install`.

## Entity Hierarchy

Full detail (inheritance tree + schema-table mapping) is the single source: [ARCHITECTURE.md - Entity Hierarchy](../docs/ARCHITECTURE.md#entity-hiyerarşisi). Summary:

```
AuditEntity (@MappedSuperclass — createdDate/updatedDate+by, OffsetDateTime, timestamptz)
  ├ SoftDeleteAuditEntity (isDeleted, deletedAt, @Version, @SQLRestriction("is_deleted = false"))
  │    └ BaseEntity (UUID id + equals/hashCode)   <- Company, User, Role, Permission, Group
  │       ├ UserAccount, UserProfile (@MapsId, extends SoftDeleteAuditEntity)
  └ GeneratedIdAuditEntity (UUID id, no soft delete)  <- RefreshToken
```

> Java field is `createdDate` (column `created_at`). The `TenantVerificationToken` entity does NOT exist yet — it arrives with K-21 (Epic 2.0.C), not yet implemented.

Rules:
- All IDs are `UUID` (`@GeneratedValue(strategy = GenerationType.UUID)`, `columnDefinition="uuid"`).
- Table names use the `t_` prefix (`t_users`, `t_roles`, ...). Constraint names: `idx_*`, `uk_*`, `fk_*`.
- `@SQLDelete` is separate on each concrete entity (table-specific SQL, `version = version + 1`).
- `@SQLRestriction("is_deleted = false")` on `SoftDeleteAuditEntity` -> inherited by all subclasses.
- Spring Data auditing (`@CreatedDate`/`@LastModifiedDate`/`@CreatedBy`/`@LastModifiedBy`) -> `OffsetDateTime` + `timestamptz`.
- **Non-soft-delete entities** extend `GeneratedIdAuditEntity` (only auditing fields, no `is_deleted`/`version`). Example: `RefreshToken` (short-lived, revoked).

## Multi-Tenancy (Schema-per-Tenant)

Strategy, request lifecycle and schema-table mapping detail are the single source: [ARCHITECTURE.md](../docs/ARCHITECTURE.md#schema-per-tenant-modeli). Persistence-specific:

- `SchemaPerTenantConnectionProvider` (`persistence/tenant/`) — validates the schema name with the regex `^[a-z0-9_]+$` (SQL injection defense); runs `SET search_path` on `getConnection`, resets on `releaseConnection`.
- `TenantIdentifierResolver` — reads `TenantContext.getCurrentTenant()`, returns `"public"` when null/blank.
- **Master schema (`public`):** `Company` (name, subdomain, emailDomain, schemaName, dbRole, status). `TenantVerificationToken` lands here after K-21 (not yet present).
- **Tenant schema (`tenant_xxx`):** User/Role/Permission/Group + join tables. Each tenant owns its data.
- **Tenant schema name:** `tenant_<subdomain>` (lowercase, dashes to `_`).
- **CompanyStatus** enum: `PROVISIONING`, `ACTIVE`, `SUSPENDED`, `TERMINATED`. **Current code only uses `ACTIVE`** (`provisionTenant` sets ACTIVE directly, single-phase sync). The other states are reserved for later phases.

## Flyway Migration

```
src/main/resources/db/migration/
├ public/   # auto-config at startup — public schema (t_companies)
└ tenant/   # programmatic at provisioning — in each tenant schema (TenantProvisioningService.provisionTenant)
```

- Public migration runs via Spring Boot auto-config; tenant migration runs programmatically via `TenantProvisioningService.provisionTenant()` (delegating to `TenantMigrationSupport`).
- **A new tenant migration that affects existing tenants requires `TenantMigrationRunner`** ([RISK-16](../docs/DECISIONS.md#risk-16) — RESOLVED) — otherwise existing tenants stay stuck on V1. The runner (in `backend`, `@Profile("!test")`) iterates `t_companies` at startup and runs `TenantMigrationSupport.migrateSchema()` per tenant schema. New tenant migrations land in `tenant/V2__...`, `tenant/V3__...` etc.; the runner applies them to existing tenants automatically.
- For H2 compatibility use the long form `TIMESTAMP WITH TIME ZONE` — the `TIMESTAMPTZ` shorthand is unsupported on H2. (Note: tenant/public migration SQL never runs on H2 — test profile has `flyway.enabled=false` + `create-drop`. Partial-index `WHERE` syntax is H2-incompatible but only ever executes on PostgreSQL.)

## Repository

Package `com.ibrhalil.systemforge.persistence.repository`. Extends `JpaRepository`. Current:
- `CompanyRepository` (`findBySubdomain`, `findByEmailDomain`, `findBySchemaName`)
- `UserRepository` (`findByEmail`, `findByUsername`)
- `RefreshTokenRepository` (`findByToken`)

> `TenantVerificationTokenRepository` arrives with K-21 (Epic 2.0.C) — not yet present.

## Gotchas

- **`ddl-auto=none` is MANDATORY** (NEVER `validate`). Schema-per-tenant + lazy tenant schema means `validate` at startup tries to verify every entity against the `public` schema -> `missing table` crash. The schema lives entirely in Flyway. (Test profile exception: `create-drop` + `flyway.enabled=false`.)
- **`@EntityScan("com.ibrhalil.systemforge.entity")`** (entities live in the `entity` package, NOT `persistence.entity`). Repositories live in `com.ibrhalil.systemforge.persistence.repository`. This split is wired by an explicit scan in `MultiTenancyJpaConfig` (in backend).
- **`hashCode()` ([DEBT-7](../docs/DECISIONS.md#debt-7) — RESOLVED):** `BaseEntity`/`GeneratedIdAuditEntity` use `id == null ? System.identityHashCode(this) : id.hashCode()` (ID-based). Do NOT put a transient (pre-persist) entity into a `HashSet`/`HashMap` key and look it up after persist — the ID flips `null→UUID` and the hash changes. Entities loaded from the DB are fine.
- **Soft-delete + UNIQUE ([RISK-17](../docs/DECISIONS.md#risk-17) — RESOLVED):** DB-level UNIQUE conflicts with soft delete (deleted row remains). Partial index required: `CREATE UNIQUE INDEX ... WHERE is_deleted = false`. Applied in `public/V2` + `tenant/V2` for all `SoftDeleteAuditEntity` subclasses — public `t_companies` (name/subdomain/email_domain/schema_name) + tenant `t_users`(username,email)/`t_roles`/`t_permissions`/`t_groups`(name). `GeneratedIdAuditEntity` subclasses (`RefreshToken`) and join tables keep normal UNIQUE.
