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
  └ GeneratedIdAuditEntity (UUID id, no soft delete)  <- TenantVerificationToken, UserAuthToken,
       PlatformUser, PlatformApiKey, PlatformAuditLog (K-50, public schema)
```

(`Plan`/`Subscription`/`TenantModule` are soft-delete `BaseEntity` in the public schema — full tree: ARCHITECTURE.md.)

> Java field is `createdDate` (column `created_at`). The K-21 entity (`TenantVerificationToken`) lives in the `public` schema. (`OrganizationDomain` was removed with the K-38 dead-code cleanup — returns with the email-domain self-register flow, Epic 2.9.)

> **Read models moved in-process ([K-49](../docs/DECISIONS.md#k-49)):** the former `UserDirectoryView` `@Immutable @Subselect` read model was REMOVED — list reads are now backend-side Criteria DTO projections (`web/projection/ProjectionListQuery`, per-feature executors). This module carries no read-model entities; a `@Subselect`/`CREATE VIEW` entity returns only if a genuinely complex/dense table demands one (exception, not the default).

> Faz 4a: `Role.parentRoles` is a self-M2M (`t_role_parents`) modeling role inheritance — a role transitively inherits its parent roles' permissions (resolved recursively in `CustomUserDetailsService`). `RoleService.setParents` enforces acyclicity. (The Faz 4b ABAC template — `Ownable` + backend `OwnershipGuard` — was removed by [K-38](../docs/DECISIONS.md#k-38) as unused; it returns with the first ownership-based module, Notes/Warehouse/Logistics.)

Rules:
- **Read-model (view) entities carry the `View` suffix** — IF one ever returns (K-49 made them exception-only; lists use backend Criteria DTO projections), any `@Immutable @Subselect` (or `CREATE VIEW`-backed) entity is named `<Entity>View`, its repository `<Entity>ViewRepository`. Makes the read-only projection role obvious at a glance and keeps it distinct from the writable entity / DTO names.
- All IDs are `UUID` (`@GeneratedValue(strategy = GenerationType.UUID)`, `columnDefinition="uuid"`).
- Table names use the `t_` prefix (`t_users`, `t_roles`, ...). Constraint names: `idx_*`, `uk_*`, `fk_*`.
- `@SQLDelete` is separate on each concrete entity (table-specific SQL, `version = version + 1`).
- `@SQLRestriction("is_deleted = false")` on `SoftDeleteAuditEntity` -> inherited by all subclasses.
- Spring Data auditing (`@CreatedDate`/`@LastModifiedDate`/`@CreatedBy`/`@LastModifiedBy`) -> `OffsetDateTime` + `timestamptz`.
- **Non-soft-delete entities** extend `GeneratedIdAuditEntity` (only auditing fields, no `is_deleted`/`version`). Examples: `TenantVerificationToken` (K-21 — single-use, `usedAt` invalidation), `UserAuthToken` (user lifecycle — email verify / password reset, digest-only, supersedes on reissue), `PlatformUser`/`PlatformApiKey`/`PlatformAuditLog` (K-50 — public-schema platform identities; all `@Table(schema="public")`). (The former `RefreshToken` example was removed with the dead code cleanup in [K-36](../docs/DECISIONS.md#k-36).)

## Multi-Tenancy (Schema-per-Tenant)

Strategy, request lifecycle and schema-table mapping detail are the single source: [ARCHITECTURE.md](../docs/ARCHITECTURE.md#schema-per-tenant-modeli). Persistence-specific:

- `SchemaPerTenantConnectionProvider` (`persistence/tenant/`) — validates the schema name with the regex `^[a-z0-9_]+$` (SQL injection defense); runs `SET search_path` on `getConnection`, resets on `releaseConnection`.
- `TenantIdentifierResolver` — reads `TenantContext.getCurrentTenant()`, returns `"public"` when null/blank.
- **Master schema (`public`):** `Company` (name, subdomain, schemaName, status — `emailDomain` removed by K-32, `dbRole` removed by [K-38](../docs/DECISIONS.md#k-38)), `TenantVerificationToken` (K-21, single-use signup tokens — [RISK-30] token stored as SHA-256 digest, admin password hash nulled after provisioning), `Plan` (K-16, reference data — `GeneratedIdAuditEntity`, seeded from the backend `PlanDefinition` enum), `Subscription` (K-16, one per company — partial unique `(company_id) WHERE is_deleted = false`, FK→Company/Plan), `TenantModule` (K-16, activation state per `(company_id, module_key)` — partial unique, key resolved against the backend `ModuleDefinition` enum, NOT an FK), `PlatformUser`/`PlatformApiKey`/`PlatformAuditLog` (K-50 — global platform identities: HUMAN superadmins + SERVICE accounts `userType` CHECK, API keys `@ManyToOne LAZY` to the user — Subscription pattern; raw key never persisted, only its SHA-256 digest via the backend `TokenHasher`; `t_platform_audit_logs` append-only).
- **Tenant schema (`tenant_xxx`):** User/Role/Permission/Group + join tables. Each tenant owns its data.
- **Tenant schema name:** `tenant_<subdomain>` (lowercase, dashes to `_`).
- **CompanyStatus** enum: `PROVISIONING`, `ACTIVE`, `SUSPENDED`, `TERMINATED`. K-21 activates `PROVISIONING` (phase 1 → phase 2 promotes to `ACTIVE`).

## Flyway Migration

```
src/main/resources/db/migration/
├ public/   # auto-config at startup — public schema (t_companies + t_platform_*)
├ tenant/   # programmatic at provisioning — in each tenant schema (TenantProvisioningService.provisionTenant)
└ module/   # per-module tenant migrations (ownMigrations=true), activated on module activation
    ├── apps/V1__app_builder.sql   # K-15 custom app builder (t_apps + JSONB EAV family)
    └── notes/V1__notes.sql        # K-44 notes module (t_notes + t_note_categories, FK ON DELETE SET NULL)
```

- Public migration runs via Spring Boot auto-config; tenant migration runs programmatically via `TenantProvisioningService.provisionTenant()` (delegating to `TenantMigrationSupport`).
- **Pre-1.0.0 squash ([K-36](../docs/DECISIONS.md#k-36)):** the former `public/V1..V3` and `tenant/V1..V8` were collapsed into the `V1.x` baseline family per location before the first production deploy (no deployed environment existed; local DBs were reset) — dotted versions run in order, one file per domain area: tenant `V1__iam_users` → `V1.1__iam_rbac` → `V1.2__audit` → `V1.3__pm_projects_tasks`; public `V1__tenant_registry` → `V1.1__signup_verification_tokens`. New migrations continue at `V2+`: public `V2__plans_subscriptions_modules.sql` (K-16) and `V3__token_hash_at_rest.sql` (RISK-30 — backfills every token to its SHA-256 hex digest + `admin_password_hash DROP NOT NULL`), tenant `V2__request_logs.sql` (K-27), `V3__project_container.sql` (K-45) and `V4__user_auth_tokens.sql` (user lifecycle — `t_auth_tokens`, digest-only single-use tokens for email verify/password reset) have landed; **public `V4__platform_identity.sql` (K-50 — `t_platform_users` HUMAN|SERVICE CHECK, `t_platform_api_keys` prefix+hash+scopes, `t_platform_audit_logs` append-only trigger with distinct function name `prevent_platform_audit_modification` to avoid search_path coupling) has landed**; the next public migration is `V5`, tenant `V5`.
- **Typed project container ([K-45](../docs/DECISIONS.md#k-45)):** `tenant/V3__project_container.sql` adds `t_projects.parent_project_id` (nullable self-FK `ON DELETE SET NULL`) + `is_default` (per-type "Genel" marker, partial unique `uk_projects_default_type`) and REPLACES the tenant-wide name unique with the per-type `uk_projects_type_name (project_type, name) WHERE is_deleted = false` — each project type is its own naming namespace (the NOTES/APPS defaults share the "Genel" name). V3 is DDL-only: the default containers are inserted by the module V2 migrations (so a tenant that never activated a module never gets one). **Ordering guarantee:** `TenantMigrationRunner` (`@Order(2)`) applies the core tree BEFORE `ModuleSyncRunner` re-syncs module trees, so `module/notes/V2__notes_project_scoping.sql` and `module/apps/V2__apps_project_scoping.sql` may safely reference `is_default` — both add `project_id UUID` (nullable → adoption-aware default ensure → backfill → `SET NOT NULL` → FK `ON DELETE CASCADE` + index) to the notes pair (`t_notes`, `t_note_categories`) and `t_apps`. The Java-side activation path mirrors the default ensure (`ModuleActivationService.ensureDefaultProjectInNewTx`). V3's checksum changed after its initial landing during the K-45 work — local dev DBs from that window must be reset.
- **Module migrations ([K-16](../docs/DECISIONS.md#k-16)):** modules with their own tables ship migrations under `db/migration/module/<key>/` — deliberately OUTSIDE `db/migration/tenant` (Flyway location scanning is RECURSIVE; a module subtree inside `tenant/` would be swept into the core history and collide on versions). They run against a per-module history table (`flyway_schema_history_mod_<key>`) via `TenantMigrationSupport.migrateModule`, so each module versions independently from V1. First real user: `apps/V1__app_builder.sql` (K-15) — PG-only DDL (`jsonb` columns, GIN `jsonb_path_ops` index, partial unique indexes); never executes on H2. Second: `notes/V1__notes.sql` (K-44) — `t_notes` (markdown `content TEXT`, `category_id` FK `ON DELETE SET NULL`, `pinned`; no title uniqueness — repeats are legitimate) + `t_note_categories` (partial-unique name); entities `Note`/`NoteCategory` follow the Task convention (plain UUID column, no `@ManyToOne`).
- **Custom app builder entities (K-15 / Faz 3.0.B):** `App`/`AppProperty`/`AppRecord`/`AppView` are soft-delete `BaseEntity` tenants (per-app partial uniques `uk_app_properties_name`/`uk_app_views_name` in PG; entity annotations carry no composite uniques — H2 create-drop stays loose). `AppRecordValue` is a **soft-delete-less `GeneratedIdAuditEntity`** (like `AuditLog`): value rows are dependent data — clear = row delete, re-set = insert (plain UNIQUE `(record_id, property_id)`). JSONB columns map as plain `String` + `columnDefinition="jsonb"` (`AuditLog` convention; NO hypersistence-utils) and rely on `stringtype=unspecified` (dev/prod JDBC URLs + the IT's Hikari property). NOTE: `AppRecordValue.value` is backtick-quoted (`@Column(name = "`value`")`) — `value` is a reserved word on H2 and quoting stays consistent with PG's lowercase folding. `AppView` is a writable domain entity (K-15's `t_app_views`), not a `@Subselect` read model — the `View` suffix convention does not apply here.
- **A new tenant migration that affects existing tenants requires `TenantMigrationRunner`** ([RISK-16](../docs/DECISIONS.md#risk-16) — RESOLVED) — otherwise existing tenants stay stuck on the current version. The runner (in `backend`, `@Profile("!test")`) iterates `t_companies` at startup and runs `TenantMigrationSupport.migrateSchema()` per tenant schema. New tenant migrations land in `tenant/V2__...`, `tenant/V3__...` etc.; the runner applies them to existing tenants automatically. `baselineOnMigrate` is intentionally NOT used (fresh DBs only since the K-36 squash — a baseline would silently skip V1 on a non-empty schema).
- For H2 compatibility use the long form `TIMESTAMP WITH TIME ZONE` — the `TIMESTAMPTZ` shorthand is unsupported on H2. (Note: tenant/public migration SQL never runs on H2 — test profile has `flyway.enabled=false` + `create-drop`. Partial-index `WHERE` syntax is H2-incompatible but only ever executes on PostgreSQL.)

## Repository

Package `com.ibrhalil.forgesys.persistence.repository`. Extends `JpaRepository` (list-bearing repos also `JpaSpecificationExecutor` — the backend filter engine queries through Specifications, with `@EntityGraph` overrides on `findAll(Spec, Pageable)` mirroring the former plain-list graphs to keep the N+1 profile). The module compiles with `hibernate-jpamodelgen` (annotation processor alongside Lombok, version property in the root pom) — generated `Entity_` classes carry compile-time field-name String constants (`User_.EMAIL`) used by the backend sort/filter whitelists. Current:
- `CompanyRepository` (`findBySubdomain`, `findBySchemaName`, `findAllTenantSchemas` — interface projection `TenantSchemaView` id+schemaName+status for the startup runners, [K-40](../docs/DECISIONS.md#k-40)) — public şema
- `PlatformUserRepository` (`findByEmail`, `existsByEmail`, `findTokenInvalidBefore` JPQL projection — platform branch of `JwtAuthenticationFilter`) — public şema (K-50)
- `PlatformApiKeyRepository` (`findByKeyPrefix`, `findByPlatformUserIdOrderByCreatedDateDesc`) — public şema (K-50)
- `PlatformAuditLogRepository` (paging via inherited `findAll`; filters via backend `AuditQueryService` pattern) — public şema (K-50)
- `TenantVerificationTokenRepository` (`findByToken` — SHA-256 digest lookup [RISK-30], `claimToken` atomic claim, `purgeStale` daily purge) — public şema (K-21)
- `PlanRepository` (`findByKey`), `SubscriptionRepository` (`findByCompanyId`), `TenantModuleRepository` (`findByCompanyId`, `findByCompanyIdAndModuleKey`) — public şema (K-16)
- `UserRepository` (`findByEmail`, `findByUsername`, `findGroupMembers`, `findTokenInvalidBefore` [RISK-21 single-col projection], `findUserIdsByRole`/`findUserIdsByGroup`/`findGroupIdsByUserId`/`findUserIdsByGroupIds`/`bulkSetTokenInvalidBefore` [Faz 1 revoke + group-member visibility scope]) — tenant şeması
- `UserAuthTokenRepository` (`findByTokenHash` — SHA-256 digest lookup, `claimToken` atomic claim, `invalidateOutstanding` supersede-on-reissue, `purgeStale` daily purge) — tenant şeması (user lifecycle, V4)
- `RoleRepository` (`findByName`, `existsByIdInAndAllPermissionsTrue`, `findAllByAllPermissionsTrue` [all-permissions flag]) — tenant şeması
- `PermissionRepository` (`findByName`, `findAllNames` [JPQL name projection], `JpaSpecificationExecutor` — K-37 paged list + `q`) — tenant şeması
- `GroupRepository` (`findByName`) — tenant şeması
- `AuditLogRepository` / `LoginHistoryRepository` — Specification-driven reads (append-only tables; the former derived filter queries were folded into the backend filter engine)
- `AppRepository` (`existsByName`/`existsByNameAndIdNot`, `existsByProjectId` — K-45 type lock, `JpaSpecificationExecutor` — app list filter engine) · `AppPropertyRepository` (ordered by position, scoped `findByIdAndAppId`, per-app name checks, `deleteValuesByPropertyId` bulk JPQL) · `AppRecordRepository` (paged by app, scoped lookups, `countByAppId` plan limit) · `AppRecordValueRepository` (`findAllByRecordIdIn` bulk fetch — list pages stay N+1-free) · `AppViewRepository` (ordered by position, scoped lookups, per-app name checks) — hepsi K-15 apps modülü, tenant şeması. The native PG JSONB search is NOT a repository — it lives in backend (`AppRecordSearchExecutor`, EntityManager).
- `NoteRepository` (`JpaSpecificationExecutor` — `?q=` + category/pinned/project filters; `existsByProjectId` — K-45 type lock) · `NoteCategoryRepository` (`existsByName`/`existsByNameAndIdNot`, `findByIdAndProjectId` consistency check, `JpaSpecificationExecutor`) — K-44 notes modülü (K-45 project-scoped), tenant şeması.
- `ProjectRepository` (`existsByNameAndType`/`existsByNameAndTypeAndIdNot` — per-type naming, `findFirstByNameAndTypeOrderByName` default adoption, `findDefaultIdsByType` — K-45) · `TaskRepository` (`existsByProjectId` — K-45 type lock).

## Gotchas

- **`ddl-auto=none` is MANDATORY** (NEVER `validate`). Schema-per-tenant + lazy tenant schema means `validate` at startup tries to verify every entity against the `public` schema -> `missing table` crash. The schema lives entirely in Flyway. (Test profile exception: `create-drop` + `flyway.enabled=false`.)
- **`@EntityScan("com.ibrhalil.forgesys.entity")`** (entities live in the `entity` package, NOT `persistence.entity`). Repositories live in `com.ibrhalil.forgesys.persistence.repository`. This split is wired by an explicit scan in `MultiTenancyJpaConfig` (in backend).
- **`hashCode()` ([DEBT-7](../docs/DECISIONS.md#debt-7) — RESOLVED):** `BaseEntity`/`GeneratedIdAuditEntity` use `id == null ? System.identityHashCode(this) : id.hashCode()` (ID-based). Do NOT put a transient (pre-persist) entity into a `HashSet`/`HashMap` key and look it up after persist — the ID flips `null→UUID` and the hash changes. Entities loaded from the DB are fine.
- **Soft-delete + UNIQUE ([RISK-17](../docs/DECISIONS.md#risk-17) — RESOLVED):** DB-level UNIQUE conflicts with soft delete (deleted row remains). Partial index required: `CREATE UNIQUE INDEX ... WHERE is_deleted = false`. Applied for all `SoftDeleteAuditEntity` subclasses in the `V1.x` baseline migrations ([K-36](../docs/DECISIONS.md#k-36)) — public `t_companies` (name/subdomain/schema_name) + tenant `t_users`(username,email)/`t_roles`/`t_permissions`/`t_groups`(name)/`t_projects` — now PER-TYPE `(project_type, name)` via `uk_projects_type_name` (tenant/V3, K-45). `GeneratedIdAuditEntity` subclasses (`TenantVerificationToken`) and join tables keep normal UNIQUE.
- **Append-only audit tables (Faz 2):** `t_audit_logs` + `t_login_history` are tamper-proof — the tenant `V1.2` migration installs a `BEFORE UPDATE OR DELETE` trigger (`prevent_audit_modification`, `check_violation`) on each. The app is insert-only by design (`AuditService`/`LoginHistoryService` only `save` new rows), so the trigger breaks nothing; a compromised app/admin role cannot rewrite history (only a true superuser bypasses triggers). `ALTER TABLE` is not row DML, so future migrations can still evolve the schema. Runs only on PostgreSQL (tenant migrations never execute on the H2 test profile). **K-50 platform audit:** `public/V4__platform_identity.sql` installs its own append-only trigger (`prevent_platform_audit_modification` — distinct function name avoids search_path coupling with the tenant trigger).
- **Role inheritance (Faz 4a):** `t_role_parents` (self-M2M on `t_roles`, created in the tenant `V1.1` baseline migration). Soft-deleted roles are filtered out of the parent traversal by `@SQLRestriction`, so orphan join rows left by a soft-deleted parent are harmless. `ON DELETE CASCADE` never fires (soft-delete is an UPDATE), consistent with the other join tables.
- **All-permissions flag:** `t_roles.all_permissions BOOLEAN NOT NULL DEFAULT FALSE`. A role carrying the flag implicitly holds every permission in the tenant — resolved dynamically by `CustomUserDetailsService.resolvePermissionNames` (checked after the parent closure, so a role inheriting from an all-permissions role is itself all-permissions). The built-in `Admin` role is seeded with the flag and carries NO explicit `t_role_permissions` rows, so deleting a catalog permission is never blocked as "in use" by Admin.
