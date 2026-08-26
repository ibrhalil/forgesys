# AGENTS.md

## Project

**ForgeSys** — modular multi-tenant SaaS platform. Java 21 + Spring Boot 4.1, PostgreSQL 16, Redis 7.4, Flyway. Hybrid model: built-in modules (pm: Projects & Tasks, apps: App Builder, notes — Odoo/ERPNext style) + tenant custom apps (Notion/Airtable style, JSONB EAV). **Schema-per-tenant** isolation; **tenant *users* live in tenant schemas; *platform* identities live in `public`** (K-50); RBAC (User-Role + Group-Role + Role-Permission, inheritance, `all_permissions` flag).

## Language Policy (token optimization)

- **Reasoning / chain-of-thought:** English.
- **AI-facing docs (all `AGENTS.md` files):** English.
- **User-facing docs (`README.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/DECISIONS.md`):** English/Turkish mix is allowed; prefer Turkish where English obscures meaning.
- **User communication (questions, answers, explanations, summaries):** Turkish.
- **Code, commit messages, file/folder names, technical terms:** English.
- **Only English and Turkish** are permitted — no other languages.

## Documentation map

- [`README.md`](README.md) — setup, running, **build commands** (single source), API, troubleshooting. (TR/mixed)
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture diagram, request lifecycle, schema-per-tenant, entity hierarchy, **config profiles** (single source). (TR/mixed)
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — remaining work + completed-epics summary (no ticket numbers). (TR/mixed)
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — decision log (K-XX architecture, RISK-XX risk, DEBT-XX tech debt) + **frozen decisions** (do not re-litigate).
- [`docs/CODE_NOTES.md`](docs/CODE_NOTES.md) — "why" notes moved OUT of source comments (long narratives live here, not in code). TR/mixed. (TR/mixed)
- [`docs/plans/`](docs/plans/) — active epic plan files (`<key>-<slug>.md`): phases, checkbox steps, claims, status log. The progress single-source for long-running/multi-agent work. (EN)
- Each module has its own `AGENTS.md`: [`common/`](common/AGENTS.md) · [`persistence/`](persistence/AGENTS.md) · [`backend/`](backend/AGENTS.md) · [`frontend/`](frontend/AGENTS.md). (all EN)

> Completed initiatives live in `docs/DECISIONS.md` (K-15..K-44, RISK-19..RISK-36) and git history — this file carries current rules only.

## Setup (summary)

Full detail and all commands live in `README.md`. Summary:

```bash
mvn clean install          # all modules (tests run on H2, no Docker required)
docker compose up -d       # db + redis (dev infra)
# backend: run/debug ForgeSysApplication from the IDE (dev profile)
# frontend: cd frontend && npm install --include=optional && npm run dev
```

- `.env` is for prod Docker Compose only; not needed in the `dev` profile. Never committed (in `.gitignore`).

## Modules

Each module has its own `AGENTS.md` with module-specific rules.

- `common/` — shared core (`TenantContext`, shared exceptions). **NO Spring/JPA.**
- `persistence/` — JPA entities + multi-tenancy infrastructure + Flyway migration.
- `backend/` — Spring Boot application (controller/service/security/config). Produces the executable jar.
- `frontend/` — React 19 + TypeScript + Vite SPA.

## Operational infrastructure (`infra/`)

Not source code; runtime/operational files. Details in `infra/README.md`.

- `infra/config/` — prod externalized override. Dropping an `application-prod.yaml` here overrides the one inside the jar (`SPRING_CONFIG_ADDITIONAL_LOCATION`). **Do not commit secrets.**
- `infra/data/` — bind-mount volumes (`postgres/` dev+prod; `redis/` prod only — dev redis uses a named volume). **Not committed.** Ownership is auto-fixed by the one-shot `data-init` compose service (postgres UID 70, redis UID 999); a wiped `infra/data` is recoverable with `docker compose up -d --force-recreate db`.
- `infra/init-sql/` — Docker postgres `/docker-entrypoint-initdb.d/` scripts. Run **only on first DB creation** (extension, role). **Completely separate from Flyway migrations** — do not mix.
- `infra/logs/` — Spring Boot file appender + container log bind-mount. **Not committed.**
- `infra/ssl/` — TLS certificates / private keys. **NEVER commit** (conflicts with the "Limits / Never" rule below).
- `infra/templates/` — externalized runtime templates (mail HTML/CSS etc.).

**init-sql vs Flyway (critical distinction):** Flyway runs every startup from `flyway_schema_history` (versioned). `init-sql/` is run by the postgres image **only when the data directory is empty** (first install). Never put the same file in both — Flyway checksum/history consistency breaks.

## Critical rules (all modules)

- **Tenant isolation is MANDATORY.** No query may skip the tenant filter. Tenant data leakage is the most critical bug class. The tenant context is established by `TenantFilter` (`common.TenantContext` ThreadLocal); do NOT validate tenant in the controller.
- **The root pom is only a lightweight parent + aggregator** — it does not impose dependencies on modules (no `<dependencies>`), only version management. No module uses `spring-boot-starter-parent` as parent.
- **Cyclic dependencies between modules are FORBIDDEN.** Dependency graph: `common` <- `persistence` <- `backend`. `frontend` is independent.
- **Versions live in the root `<properties>`** (`spring-boot.version`, `java.version`). Module poms do not pin versions.
- **IDs are UUID everywhere** (`GenerationType.UUID`). Table names use the `t_` prefix.
- **Code style:** package `com.ibrhalil.forgesys.*`, DTOs are `record`, centralized error handling via `@RestControllerAdvice` (`ApiErrorResponse` + `ErrorCode`), Lombok in the backend module.
- **Comment policy:** code comments carry at most 1-3 lines of contract/critical-invariant — never long narratives. "Why" stories go to `docs/CODE_NOTES.md` (add a section + point to it); decision history goes to `docs/DECISIONS.md`. Do not re-grow essay javadocs.

## Engineering principles

General engineering conduct. Project-specific rules above take precedence; the system prompt covers comments, output brevity, and conventions.

- **Investigate before implementing.** Search for existing implementations, reusable components, and conventions first. Prefer improving existing code over introducing new code. State assumptions explicitly when requirements are ambiguous.
- **No speculative code.** Solve exactly the requested problem — no gold plating, no abstractions that do not solve a real problem today; plans live in DECISIONS.md, not in code (frozen decision #21).
- **Query performance.** Check for N+1 before proposing ORM solutions; favor `@EntityGraph`/`JOIN FETCH` for lazy associations. Multi-tenant queries multiply cost — every query crosses a tenant schema.
- **Thread safety.** `TenantContext` is a `ThreadLocal` — it does NOT propagate across `@Async`/executor threads without a `TaskDecorator` ([RISK-10](docs/DECISIONS.md)). Always `clear()` in `finally`.
- **Backward compatibility.** Do not break endpoint contracts (`/api/v1/*`) without explicit intent. Deprecate before removing; version when behavior changes.
- **Documentation = part of development.** Every significant change carries its doc delta: ADR → DECISIONS.md; endpoint → module AGENTS.md; architecture impact → ARCHITECTURE.md.

## Working mode (long-running & multi-agent work)

Sessions may end mid-epic and multiple agents may work in parallel — the user must never have
to re-explain context. State lives in files, not in conversation.

- **Plan file first.** Any multi-phase work gets a plan file under
  `docs/plans/<key>-<slug>.md` (phases, checkbox steps, per-step verify command, claims,
  status log) BEFORE implementation starts. Create it in the epic's first session.
- **Resume protocol.** An agent resuming work reads the plan file first, finds the first
  unchecked step, verifies code state against it (steps may have landed untracked), and
  continues from there. Do not ask the user for context that the plan file carries.
- **Step contract.** Every checked step leaves the repo green (`mvn clean install`; frontend
  also `npm run lint && npm test`). Never stop mid-step; a half-done step stays unchecked with
  a one-line note in the plan file.
- **Parallel agents / claims.** Before starting a phase, claim it in the plan file's claims
  table (scope + steps + files you touch, with a date). Do not edit files outside your claim.
  Shared chokepoint files (root pom, `SecurityConfig`, `PermissionCatalog`, `AGENTS.md`,
  `DECISIONS.md`, the plan file's structure sections) are edited only with a claim and are
  re-read immediately before editing.
- **Status log.** Append a line to the plan file's status log whenever a phase lands, a
  decision refines, or a blocker appears. Epic-level outcomes (ADR, ROADMAP, module docs) are
  recorded as phases land, not deferred to the end.
- Plans live in `docs/plans/`, never in code comments (#21). When an epic completes, its plan file is **deleted**; decision/impact records graduate to `DECISIONS.md`.

## Test

- Config profiles (dev/prod/test, H2, ddl-auto, flyway.enabled) are the single source: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#konfigürasyon-profilleri).
- Add at least one test per new endpoint. Extra care on changes touching tenant isolation.
- Frontend: a new feature does not merge without tests (Vitest + RTL, `npm test`).

## Limits

**Never:**
- **Run git operations without authorization.** `git commit`, `git push`, `git amend`, `git merge`, `git rebase`, `git reset --hard`, branch creation/deletion, `gh pr create`, etc. — ALL of these happen ONLY when the user explicitly asks. Do NOT take initiative like "I'm done, let me commit." Stay in staging until the user says commit/push. `git add`/`git status`/`git diff`/`git log` (read-only) are fine.
- Do not commit/read `.env`, `application-prod.yaml` secrets, or RSA keys (`certs/*.pem`).
- Do not set `ddl-auto` to `validate` (multi-tenant + lazy tenant schema crashes at startup — always `none`, the schema lives in Flyway). Test profile exception: `create-drop`.
- Do not write cross-tenant queries. Do not log sensitive data (password, token, PII).

**Ask first:**
- Before adding a new Flyway migration (especially if it affects existing tenant schemas — `TenantMigrationRunner` is required, see [RISK-16](docs/DECISIONS.md)).
- Before adding a new dependency (first check whether the root pom accommodates it).

**Always:**
- Add a test for a new endpoint.
- Use `@Transactional` (method-level; `readOnly=true` for lookups) for service-layer write operations. **Exception:** `provisionTenant` is currently non-transactional ([DEBT-10](docs/DECISIONS.md)); fixed with K-21.

## Git

> **The rules below apply ONLY when the user explicitly asks for a commit/push/PR.** An agent must not commit, push, amend, merge, create/delete a branch, or open a PR on its own — see "Limits / Never" above. Leave changes in staging; do not act until the user says `git add`/`commit`/`push`/`gh pr create`.

- **Branch:** `feat/SF-NN-kisa-aciklama` — the developer chooses their own `SF-NN` number; it is not tied to the roadmap. Branch is deleted after merge.
- **Commit:** Conventional Commits — `feat(tenant): add subdomain resolver`, `fix(auth): handle expired token`, `refactor: ...`, `test: ...`, `docs: ...`, `chore(deps): ...`. Subject <72 chars, lowercase, no period, imperative mood.
- All PRs target `develop`. Squash merge. Before a PR: `./mvnw test` + `npm run lint` + `npm test`.
