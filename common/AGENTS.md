# common/AGENTS.md

## Module

Shared core — `TenantContext` (ThreadLocal), shared exceptions. Used by both the web layer (`backend.TenantFilter`) and the persistence layer (`SchemaPerTenantConnectionProvider`). General rules from the root AGENTS.md apply.

## Rules (MANDATORY)

- **Spring/JPA dependency is FORBIDDEN.** This module is a lightweight core; `pom.xml` must contain only `slf4j-api` (compile) + `junit-jupiter` (test). Do NOT add `spring-boot-starter-*` — it defeats the module's purpose.
- Package: `com.ibrhalil.forgesys.common.*` — subpackages `tenant/`, `exception/`.
- A type belongs here only if shared by more than one module; types used solely by `backend` go in `backend`, not here.

## Current contents

- `common/tenant/TenantContext` — `final` utility class, `ThreadLocal<String>`. Static methods: `setCurrentTenant(String)`, `getCurrentTenant()`, `clear()`. `TenantFilter` sets it per request; `clear()` must be called in `finally` (no ThreadLocal leak).
- `common/exception/TenantNotFoundException` — `RuntimeException`. Thrown by persistence, mapped to HTTP 400 by `backend.GlobalExceptionHandler`.

## Test

```bash
./mvnw -pl common test
```

`TenantContext` unit tests exist. Add a leak test for any new ThreadLocal-using code.
