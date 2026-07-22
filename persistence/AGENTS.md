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

- Public migration runs via Spring Boot auto-config; tenant migration runs programmatically via `TenantProvisioningService.provisionTenant()`.
- **A new tenant migration that affects existing tenants requires `TenantMigrationRunner`** ([RISK-16](../docs/DECISIONS.md#risk-16)) — otherwise existing tenants stay stuck on V1.
- For H2 compatibility use the long form `TIMESTAMP WITH TIME ZONE` — the `TIMESTAMPTZ` shorthand is unsupported on H2.

## Repository

Package `com.ibrhalil.systemforge.persistence.repository`. Extends `JpaRepository`. Current:
- `CompanyRepository` (`findBySubdomain`, `findByEmailDomain`, `findBySchemaName`)
- `UserRepository` (`findByEmail`, `findByUsername`)
- `RefreshTokenRepository` (`findByToken`)

> `TenantVerificationTokenRepository` arrives with K-21 (Epic 2.0.C) — not yet present.

## Gotchas

- **`ddl-auto=none` is MANDATORY** (NEVER `validate`). Schema-per-tenant + lazy tenant schema means `validate` at startup tries to verify every entity against the `public` schema -> `missing table` crash. The schema lives entirely in Flyway. (Test profile exception: `create-drop` + `flyway.enabled=false`.)
- **`@EntityScan("com.ibrhalil.systemforge.entity")`** (entities live in the `entity` package, NOT `persistence.entity`). Repositories live in `com.ibrhalil.systemforge.persistence.repository`. This split is wired by an explicit scan in `MultiTenancyJpaConfig` (in backend).
- **`hashCode()` bug ([DEBT-7](../docs/DECISIONS.md#debt-7)):** `BaseEntity`/`GeneratedIdAuditEntity` use `Objects.hash(getClass())` -> same hash for all entities of a type -> `Set<Role>` collisions. Must be fixed before RBAC.
- **Soft-delete + UNIQUE ([RISK-17](../docs/DECISIONS.md#risk-17)):** a DB-level UNIQUE constraint conflicts with soft delete (the deleted row remains). A partial index is required: `CREATE UNIQUE INDEX ... WHERE is_deleted = false`. Only for `SoftDeleteAuditEntity` subclasses; `GeneratedIdAuditEntity` subclasses (`RefreshToken`) use a normal UNIQUE.
