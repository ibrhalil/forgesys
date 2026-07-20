# `infra/` — Runtime infrastructure

This directory centralizes everything the running stack needs at runtime but
which is **not** source code. Source code lives in `common/`, `persistence/`,
`backend/`, `frontend/`; this directory holds the operational layer.

## Layout

| Path              | Purpose                                                              | Committed?                       |
|-------------------|----------------------------------------------------------------------|----------------------------------|
| `config/`         | Externalized Spring Boot overrides for prod (`SPRING_CONFIG_ADDITIONAL_LOCATION`). Empty by default — drop `application-prod.yaml` here to override jar-baked values. | Directory only (`.gitkeep`)       |
| `data/postgres/`  | PostgreSQL bind-mount volume (dev + prod).                           | **No** (runtime data)             |
| `data/redis/`     | Redis AOF bind-mount volume.                                         | **No** (runtime data)             |
| `init-sql/`       | Postgres `/docker-entrypoint-initdb.d/` scripts (run once on init).  | Yes                               |
| `logs/`           | Spring Boot + container log bind-mount.                              | **No** (runtime data)             |
| `ssl/`            | TLS certificates / private keys for Nginx and app HTTPS.             | **No** — secrets, never committed |
| `templates/`      | Externalized runtime templates (e.g. email HTML/CSS).                | Yes (templates can be committed)  |

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

## Bind-mount UID matrix (PostgreSQL / Redis)

Container UID'leri (`postgres:16-alpine` → 70, `redis:7.4-alpine` → 999) host
dizinlerine yazabilmeli. Gerekli işlem platform'a göre değişir:

| Platform                     | UID işlemi gerekiyor mu? | Komut                                                                          |
|------------------------------|--------------------------|--------------------------------------------------------------------------------|
| Docker Desktop (macOS)       | **Hayır**                | VirtioFS user-namespace mapping otomatik                                       |
| Docker Desktop (Windows WSL2)| **Hayır**                | WSL2 otomatik map eder                                                         |
| **Linux native Docker**      | **Evet — zorunlu**       | `sudo chown -R 70:70 infra/data/postgres && sudo chmod 700 infra/data/postgres`<br>`sudo chown -R 999:999 infra/data/redis` |

Linux native Docker'da (`apt`/`dnf` ile kurulan Docker Engine) user-namespace
mapping yoktur; host dizinleri container UID'lerine sahip olmazsa postgres
`initdb` veya redis AOF rewrite sırasında crash eder. Docker Desktop ise
container UID'lerini host kullanıcısına map'leyerek bunu çözer.

Tekrarlayan izin sorunlarında, dev compose'u named volume'a geri çevirmek
pragmatik bir kaçıştır (git history'de eski konfig var).
