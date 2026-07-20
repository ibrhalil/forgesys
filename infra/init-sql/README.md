# `infra/init-sql/` — Docker postgres initialization

Files here are bind-mounted to the postgres image's
`/docker-entrypoint-initdb.d/` directory and executed **only on first DB
creation** — i.e. when the data directory is empty. They run in alphabetical
order, and may be `.sql`, `.sql.gz`, or `.sh`.

## What belongs here

- `CREATE EXTENSION` statements (uuid-ossp, citext, pg_trgm, ...)
- Role / database bootstrap that must exist before Flyway runs
- Replication / backup role setup

## What does NOT belong here

- Versioned schema migrations. Those are managed by **Flyway** under
  `persistence/src/main/resources/db/migration/` and run on every application
  startup (public schema via Spring Boot auto-config; tenant schemas via
  `TenantProvisioningService`).

## Why the split

Flyway tracks applied migrations in `flyway_schema_history` and runs every
startup; Docker init scripts run only once. Mixing them causes either
double-application (errors) or losing migration history on rebuilds.
