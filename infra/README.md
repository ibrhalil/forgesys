# `infra/` — Runtime infrastructure

This directory centralizes everything the running stack needs at runtime but
which is **not** source code. Source code lives in `common/`, `persistence/`,
`backend/`, `frontend/`; this directory holds the operational layer.

## Layout

| Path              | Purpose                                                              | Committed?                       |
|-------------------|----------------------------------------------------------------------|----------------------------------|
| `config/`         | Externalized Spring Boot overrides for prod (`SPRING_CONFIG_ADDITIONAL_LOCATION`). Empty by default — drop `application-prod.yaml` here to override jar-baked values. | Directory only (`.gitkeep`)       |
| `data/postgres/`  | PostgreSQL bind-mount volume (dev + prod).                           | **No** (runtime data)             |
| `data/redis/`     | Redis AOF bind-mount volume (**prod only** — dev uses a named volume). | **No** (runtime data)            |
| `init-sql/`       | Postgres `/docker-entrypoint-initdb.d/` scripts (run once on init).  | Yes                               |
| `logs/`           | Spring Boot + container log bind-mount.                              | **No** (runtime data)             |
| `ssl/`            | TLS certificates / private keys for Nginx and app HTTPS.             | **No** — secrets, never committed |
| `templates/`      | Externalized runtime templates — mail HTML overrides (copy `tenant-verify.<lang>.html` here; `MAIL_TEMPLATES_DIR` points the backend at this dir; missing files fall back to classpath defaults). | Yes (templates can be committed)  |

> **Redis data — dev vs prod:**
> - **dev** (`docker-compose.yml`): Docker-managed **named volume** (`redis-data`), NOT under `infra/data/`. The earlier dev bind-mount to `./infra/data/redis` caused recurring permission errors on the host because the container wrote `appendonlydir/` as UID 999, which host scanners (IDE watchers, `git`, `find`) couldn't traverse — especially on Windows/WSL2. Named volume isolates Redis's AOF data inside Docker's own storage, eliminating the host-level UID mismatch entirely.
> - **prod** (`docker-compose-prod.yml`): **bind-mount `./infra/data/redis`** (alongside postgres data — single backup/inspection point on the host). Ownership is fixed by the `data-init` one-shot service (`chown -R 999:999`); the redis container gets `DAC_OVERRIDE` to traverse the 700 `appendonlydir/` on restart-with-existing-data.

## PostgreSQL data layout

The postgres container's `PGDATA` is set to `/var/lib/postgresql/data/pgdata`
(a subdirectory of the bind-mount), not the mount point directly. This is the
**official postgres image recommendation** — bind-mount points often contain
invisible files (`.gitkeep`, `.DS_Store`, Docker metadata) that make `initdb`
refuse to initialize with "directory exists but is not empty".

Result on the host:

```
infra/data/postgres/
├── .gitkeep                  # tracked placeholder (ignored by postgres)
└── pgdata/                   # actual PGDATA — created by initdb on first run
    ├── PG_VERSION
    ├── base/
    └── ...
```

## Bind-mount vs Flyway

`init-sql/` and `persistence/.../db/migration/` are **different mechanisms**:

- `init-sql/` → Docker postgres image, runs **once** at DB creation.
- Flyway migration → runs **every** startup, versioned via `flyway_schema_history`.

See `init-sql/README.md` for the rationale.

## Bind-mount UID ownership (`data-init`)

`docker-compose*.yml` declares a one-shot **`data-init`** service (alpine) that
runs `chown` (postgres UID 70, redis UID 999) on the bind-mount dirs before
`db`/`redis` start (`depends_on: service_completed_successfully`). This makes
ownership automatic on every platform — including Linux-native Docker without
user-namespace mapping — and makes a wiped `infra/data` fully recoverable
(`docker compose up -d --force-recreate db`).

| Platform                      | Manuel `chown` gerekli mi? |
|-------------------------------|----------------------------|
| Docker Desktop (macOS/WSL2)   | Hayır (VirtioFS mapping + `data-init`) |
| **Linux native Docker**       | Hayır (`data-init` otomatik) |
