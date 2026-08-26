# CODE_NOTES — Koddan taşınan tasarım notları

> Bu dosya, kod içinden uzun yorum blokları olarak taşınan **"neden böyle"**
> bilgilerinin adresidir. Kural (kök `AGENTS.md` → Code style): koddaki yorum
> yalnızca 1-3 satırlık sözleşme / kritik uyarı taşır; uzun anlatılar buraya
> gelir. Kritik why-not'ların AI-yönelimli özetleri modül `AGENTS.md`'lerinde
> de yaşar; karar tarihçesi `docs/DECISIONS.md`'dedir (K-XX / RISK-XX / DEBT-XX).

## Nasıl kullanılır

- Kodda bir davranışın gerekçesi gerekirse: kısa yorum + buraya bakış.
- Yeni giriş eklerken bölüm başlığı altına, dosya/sınıf adını yazarak ekle.
- Taşınan bilgi burada tek kaynak olarak yaşar; koddaki kopyası silinir.

---

## backend/service — exception

### UserService
- `FILTER_FIELDS` (K-49): joined profile/account columns resolve through to-one LEFT joins; `roleCount`/`groupCount` through correlated subqueries; `roleIds`/`groupIds` are collection-membership filters (IN = has any of these, IS_NULL = none at all). Same set is the sort whitelist for `GET /users` and `POST /users/search`.
- Visibility scope (`applyVisibilityScope`): `iam:user:read` = unrestricted; `iam:group-member:read` = members of caller's own groups + self. Row-level visibility INSIDE the tenant, resolved as one extra `IN` predicate in the same query — tenant-schema isolation untouched.
- `assertViewable`: detail-scope guard for `findById`/`effectivePermissions`; throws 403 `auth_access_denied`. Self-service `/users/me` passes trivially (self always in scope).
- `activity`: last-failed-attempt comes from the append-only login history (K-19); the extra indexed single-row query is kept OUT of `toResponse` so mutation responses don't pay for it.
- `create`: optional roles/groups at creation validated up front (404 unknown ids); no session revoke needed because a brand-new user has no outstanding tokens. Email verification is optional-policy (user can log in immediately; mail only verifies the address) and best-effort AFTER commit — creation must never depend on SMTP; admin can resend.
- `verifyEmail`: idempotency design — a re-clicked link whose token is consumed succeeds silently WHEN the user is already verified (the common case); only a genuinely unusable token errors. Runs WITHOUT authenticated caller; tenant scope from `TenantFilter` (link is subdomain-anchored).
- `resendVerification`: fail-loud unlike creation's best-effort send — caller explicitly asked; SMTP failure rolls back tx + fresh token.
- `requestPasswordReset`: no account enumeration — unknown email, disabled account, and mail failure indistinguishable; even SMTP errors swallowed + logged (500-vs-200 would leak existence). Token single-use, short configured TTL, link subdomain-anchored.
- `resetPasswordWithToken`: RISK-21/K-34 revoke chain (same as admin reset) — `tokenInvalidBefore` kills outstanding access tokens, refresh tokens dropped. Audited `user_password_reset_self`, actor "system" fallback (unauthenticated).
- `update` (disable path): last-admin guard BEFORE save works because the JPQL existence check auto-flushes the pending `enabled=false` (sees post-mutation state); re-enable/no-op toggles fall through. Disabled user's tokens die immediately (JWT filter does not re-read account flags per request; login-side enable check is the complementary side-fix 1).
- `delete`: self-delete forbidden unconditionally (historical tenant-lockout cause: admin soft-deleting themselves). Last-admin check AFTER the soft-delete flush — violation rolls back the whole tx (delete undone) and the revoke never fires (no Redis-side carnage on rejection). Token revoke targets the row directly (independent of soft-delete).
- `unlock` (RISK-22): lock leaves refresh tokens in place (they work again once unlocked) → no session revive needed; no-op state-wise for a non-locked account (still audited).
- `setRoles`/`setGroups` (Faz 1): a set change can drop permissions outstanding tokens still carry — revoke immediately, not at access-token TTL. Guard auto-flushes the join-row removal first.
- `changePassword` (RISK-21): `tokenInvalidBefore = now()` rejects every pre-change access token in `JwtAuthenticationFilter`; multi-device logout by design. Granular single-session revoke deferred to Epic 2.6 (Redis blacklist).
- `resetPassword` (admin): no current-password verify (caller holds `iam:user:write`); same multi-device revoke semantics.
- `invalidateTokens` (RISK-21 + K-34 + Faz 1): centralized delegate to `SessionRevocationService.revokeUser` — stamps `tokenInvalidBefore` AND drops refresh tokens so a stolen refresh cannot mint a fresh access token whose `iat` post-dates the revoke.

### TenantProvisioningService
- Two-phase flow (K-21): phase 1 light + fully transactional (no DDL/Flyway/admin — squatting is cheap); phase 2 heavy + synchronous. Replaced the legacy single-phase `provisionTenant` (removed with the `email_domain` column).
- DEBT-10 (partial): `verifyAndProvision` is `@Transactional` for Company/token/user writes, but `CREATE SCHEMA` is an implicit commit in PostgreSQL — DDL escapes the tx. Partial-write recovery = idempotency (`CREATE SCHEMA IF NOT EXISTS`, token `usedAt` guard), not rollback.
- `self` ObjectProvider (RISK-26 fix): `createAdminUser`'s REQUIRES_NEW only works through the Spring proxy. Why REQUIRES_NEW at all: the outer `verifyAndProvision` tx holds a public-schema connection acquired BEFORE `TenantContext` switches; the admin write + RBAC seed must run in a fresh tx that re-resolves the tenant schema (session resolves schema at open) — otherwise the write goes to `search_path=public` and fails "relation does not exist".
- `verifyAndProvision` token semantics: RISK-25 — claim is an atomic conditional UPDATE (`claimToken`), NOT read-modify-write; two concurrent verifies sharing a link cannot both pass `isUsed()` and double-provision. First caller wins (claim=1), second gets `TENANT_TOKEN_ALREADY_USED`. Validity/expiry still SELECTed first so the precise error code is preserved. RISK-30 — presented raw token SHA-256-hashed before every lookup/claim (only digests in DB); `adminPasswordHash` nulled after `createAdminUser` succeeds (managed entity, flushes at commit; a rollback restores the hash so a DEBT-10 retry still finds it).
- Managed-entity sync after claim: `verification.setUsedAt(claimedAt)` avoids a redundant second UPDATE.
- TenantContext set BEFORE the REQUIRES_NEW proxy call: `CurrentTenantIdentifierResolver` resolves at session-open time; `createAdminUser` also sets defensively and clears in finally.
- FREE subscription + default module activations (K-16): `activateForCompany` manages its own TenantContext + REQUIRES_NEW; `pm` needs no extra migration (baseline tables).
- `registerSampleDataSeed` (K-47): afterCommit because the seed's REQUIRES_NEW tx must SEE the committed `t_tenant_modules` activation records + FREE subscription; under read-committed an inner tx cannot — a same-tx call fails the module gate (`MODULE_NOT_ACTIVE`) and plan chain (`SUBSCRIPTION_NOT_FOUND`) and is silently swallowed by the seed's fail-safe. Guard covers non-transactional callers (unit tests). Two-layer fail-safe: `seedForCompany` catches internally AND the callback catches again.
- `PendingSignup` carries the RAW token (RISK-30 hash-at-rest): raw goes to the mail link or, on bootstrap, straight to `verifyAndProvision` in memory.
- `createAdminUser`: admin credentials pre-hashed at phase 1, stored verbatim phase 2 (no re-hash). `assignAdminTo(user)` is the ONLY automatic Admin grant (no-op in `test`); startup seeding never touches user role assignments — privilege-escalation fix 2026-08-16 (role-less users used to be silently granted the all-permissions Admin role on every restart).
- `createDefaultSubscription`: fails fast when the FREE plan row is missing — `PlanSyncRunner` seeds plans before any provisioning can run. Real plan selection/upgrades arrive in Faz 6.
- Schema name derivation: subdomain lowercased, non-[a-z0-9-] stripped, `-`→`_`, `tenant_` prefix, regex-validated.

### AuthService
- No user-enumeration oracle: unknown email and wrong password both → `auth_bad_credentials`; remaining attempt count never surfaced; timing defense — unknown email still pays the bcrypt cost (discarded encode).
- Login ordering rationale: lock check (RISK-22) BEFORE password compare (no timing/attempt leak; expired lock resets the counter); disabled check AFTER password compare (unknown-email vs disabled not probeable without valid credentials; refresh already re-checks isEnabled, K-34).
- `login` `noRollbackFor = AuthException`: failed-attempt counter / lockout write happens right before the `badCredentials` throw and must survive it.
- Lazy pepper migration (K-23): successful login with a legacy pepper-less BCrypt hash is rehashed inline (`{sf-peppered}` marker), no extra round-trip.
- K-28: login captures device IP + User-Agent from `RequestContext` (populated by `RequestMetadataFilter`); store keeps them with the refresh-token hash so the session list shows "where you're logged in".
- Faz 5a session cap: `enforceSessionLimit` after issue — over cap, oldest sessions evicted; login always succeeds.
- `refresh` (K-34): authorities re-resolved from DB (permission changes + locked/disabled take effect at refresh); session tenant bound to request tenant (cross-tenant replay rejected, mirrors RISK-19 — freshly rotated token dropped then rejected); `noRollbackFor` keeps reuse revocation committed. Reuse detection: consumed token → revoke user's refresh tokens + `tokenInvalidBefore` (outstanding access tokens die) → `auth_refresh_token_reuse`.
- `logout` (K-34) per-session: consumes this device's refresh + blacklists the access `jti`; `tokenInvalidBefore` deliberately NOT set (that stays the nuclear path for password change/reset/reuse).
- `registerFailedAttempt` (RISK-22 + Faz 1): 5 attempts / 15 min lock; on lock `tokenInvalidBefore` is also stamped — a locked account is treated as suspected compromise, live sessions killed on the spot; refresh already blocked while locked (`accountNonLocked` re-check), so refresh tokens are left in place and work again after the window expires.

### GlobalExceptionHandler
- `NoResourceFoundException`: Spring 6.1+ static-resource chain throws this instead of a plain 404; mapped to standard `resource_not_found` (e.g. `/v3/api-docs` in a springdoc-disabled profile, K-41 prod gating).
- `AccessDeniedException`: method-level `@PreAuthorize` denials throw `AuthorizationDeniedException` (subclass) from the controller method, surfacing at the MVC layer — caught here, NOT by the filter-chain `RestAccessDeniedHandler`; 403 matches the wire contract.
- Client-error mappings (Faz D / RISK-29): malformed JSON body (`HttpMessageNotReadableException`), type-mismatched path/query params, missing `@RequestParam`, unknown sort property (`PropertyReferenceException`), and method-level Bean Validation all → 400 `validation_error` — without the handlers the catch-all turns each into a 500. Known property PATHS stay closed via `SortGuard` at the controller (handler only sees unknown property names). `ConstraintViolationException` handler is defensive/forward-compatible — controllers use `@Valid` → `MethodArgumentNotValidException`; kept so a future `@Validated` controller stays 400.
- `DataIntegrityViolationException` (RISK-28): TOCTOU uniqueness race — two requests pass the service `existsBy*`, one hits the DB constraint. Mapped to 400 with the precise `*_TAKEN` code via constraint-name substring map (`users_email`→`user_email_taken`, `users_username`, `roles_name`, `groups_name`, `permissions_name`, `projects_name`/`projects_type_name`, `note_categories_name`, `apps_name`, `app_properties_name`, `app_views_name`, `tenant_modules_company_module`→`module_already_active`, `companies_subdomain`/`companies_schema_name`→`company_subdomain_taken`); unknown → `business_error` fallback, never 500. Service checks stay (defense-in-depth).
- `DataAccessException` → 503 `service_unavailable` (Redis/DB down — e.g. refresh-token `issue` that cannot persist): "retry later" distinguishable from a real bug; `DataIntegrityViolationException` keeps its more specific handler.
- Sensitive rejected values (`password`/`token`/`secret`/`credential`) masked to `[REDACTED]` for both `@RequestBody` field errors and `ConstraintViolation` invalid values.

### UserTokenService
- Conventions (RISK-30/RISK-25): hash-at-rest (only SHA-256 digest persisted; raw goes straight into the mailed link); re-issue supersedes (new token of a purpose stamps the user's outstanding tokens of that purpose `usedAt` — only the newest link works); atomic claim (conditional UPDATE; two concurrent consumers cannot both win).
- Error codes mirror tenant-signup semantics: `user_token_invalid` / `user_token_expired` / `user_token_already_used`; purpose mismatch → `user_token_INVALID` (a password-reset link must not consume an email-verify token).
- `peek`: digest lookup without consuming, whatever the used/expired state — the idempotency probe behind `UserService.verifyEmail`.
- Tenant-scoped via the caller's `TenantContext` (request filter or a set-and-restore window).
- `purgeStaleForCurrentTenant` (RISK-30): TokenPurgeJob hook; own tx per tenant schema via the job's set-and-restore window.

### SessionService
- K-28. Sessions scoped to request tenant + owner user; self endpoints self-scope, admin endpoints take an explicit user id.
- Single-session revoke semantics: the `tokenInvalidBefore` stamp is USER-scoped (the only immediate lever without per-session `jti` storage) — sibling devices briefly 401 then recover via their still-valid refresh token; the targeted device (whose refresh was dropped) is fully signed out. `revokeAllUserSessions` additionally drops every refresh token.

### RoleService
- `FILTER_FIELDS` (K-49): `permissionCount` subquery counts explicit grants only (0 for all-permissions roles); `permissionIds`/`parentIds` membership filters.
- `ALL_PERMISSIONS_SENTINEL`: audit-delta sentinel marking the all_permissions mode in old/new value JSON.
- `delete`: join tables `t_user_roles`/`t_group_roles` are owned by User.roles/Group.roles — leftover join rows after soft-delete keep managed collections referencing a deleted role → flush fails `TransientPropertyValueException` + orphan rows; hence detach BEFORE soft-delete. Revoke targets (direct + via active groups) resolved while the role is still visible (pre-`@SQLRestriction`); guard AFTER flush sees the deleted role (admin-closure queries) and runs BEFORE the revoke so a rejected delete leaves no Redis-side carnage.
- `setPermissions`: last-admin guard BEFORE save — clearing the `all_permissions` flag (or emptying the role) can drop every admin below the one-active-admin floor; closure queries auto-flush the pending change first. Faz 1 revoke: holders' outstanding tokens still embed the old permission set.
- `setParents` (Faz 4a): acyclicity — no self-parent, no candidate that transitively inherits from this role (`reaches` DFS). Faz 1 revoke + Faz 2b audit delta; guard covers inheritance-edge removal (e.g. removing an all-permissions parent).
- Persistent-collection mutation: clear+addAll on the managed collection, never replace the reference.

### GroupService
- `FILTER_FIELDS` (K-49): `memberIds` is an inverse-membership field — join table owned by `User.groups`, resolution starts from the member side.
- `effectivePermissions`: union of the group's roles' permissions expanded through transitive parents (a group carrying an admin role makes its members admins).
- `update(deactivate)`: existence query auto-flushes pending `active=false` (skips inactive groups) → guard sees post-mutation state. Deactivation drops every member's group-granted permissions (`resolveAuthorities` skips inactive groups) → revoke; activation grants at next login → no revoke.
- `delete`: `t_user_groups` owned by `User.groups` — same detach-before-soft-delete rationale as RoleService; guard after flush, revoke after guard.
- `setMembers`: replace semantics through each user's group set (User owns the join). Only REMOVED members are revoked (they lose permissions); added members gain at next login — adding someone to a group must not log them out. Guard: removing the last admin from an admin-carrying group (auto-flushed).

### PermissionService
- `create`: a new permission joins the all-permissions set dynamically — holders of an `all_permissions` role (Admin + any "ALL" role) should see it on their next request, not at access-token TTL (outstanding tokens embed the prior snapshot) → `revokeAllPermissionsRoleHolders`.
- `update` rename: changes the authority string every all-permissions user carries → same refresh rationale.
- `delete`: `permission_in_use` (409) while assigned to any role — deleting would silently shrink every bearer's authority set; unassigning already revokes via `RoleService.setPermissions`.

### ProjectService
- K-45: `type` decides which module's content lives inside; creatable catalog = ACTIVE modules' types; default container id is the top-nav fallback target.
- Name uniqueness is PER-TYPE (`uk_projects_type_name` — the notes/apps defaults may share the "Genel" name).
- `update` guards: default container's type/parent frozen (409 `project_default_immutable`); type change forbidden while content exists (`project_type_change_forbidden`); type activation gate only when the type ACTUALLY changes (a rename of a project whose module went inactive stays allowed — content merely read-only elsewhere); parent change re-validated (404 + cycle 409).
- `assertParentAcceptable`: ancestor chain walked Hibernate-visibly — a soft-deleted mid-chain row ends the walk (a deleted node's frozen parent link cannot be extended by this update, so no cycle through it); depth capped at 50 (`MAX_PARENT_DEPTH`, no unbounded traversal).
- `currentCompany()`: mirrors the Hibernate resolver's `public` fallback for an unset context — the H2 test layout; production always has the filter-set context and no company owns "public" → degrades to `TenantNotFoundException`.

### TaskService
- Task always reached through its owning project; a task of another project is not addressable (404, no leak). Project + assignee existence validated explicitly → clean 404 instead of a DB integrity 500.

### NoteService
- K-44 re-scoped by K-45: flat list = cross-container view (`?projectId=` narrows); creates via nested endpoints or flat path defaulting to "Genel" (`ProjectContainerSupport`). Visibility tenant-wide (`notes:note:read` sees all tenant notes); personal/ABAC notes deferred. Names resolved server-side batched per page (no per-row lookups).
- `FILTER_FIELDS`: `categoryName`/`projectName` correlated scalar subqueries over plain FK columns — soft-deleted references yield null.
- `validateCategory`: category must exist (404) AND belong to the same container (409 `note_category_project_mismatch`).

### NoteCategoryService
- Categories are design-bounded data (a handful per container) → plain paged read + `q` name search.
- Project fixed at create (a move would strand notes in the old container; `projectId` change on update rejected 409). Per-project taxonomy, but name uniqueness stays TENANT-wide for now (cross-container same names are legal siblings).
- `delete`: FK's `ON DELETE SET NULL` never fires (soft-delete is an UPDATE); `@SQLRestriction` hides the row from reads; notes keep their `categoryId` value and `resolveCategoryName` treats a soft-deleted category as absent (name chip simply disappears).

### AppBuilderService
- K-15 / Faz 3.0.B re-scoped by K-45: apps live in APPS-type containers; flat writes default to "Genel"; PUT moves apps between APPS containers. Plan limits (`PlanLimitService`) are TENANT-level (not per container), soft-blocked on create. TOCTOU posture: `existsBy*` pre-check + `DataIntegrityViolationException` constraint-map fallback (RISK-28).
- Property definition validation: SELECT needs non-empty, distinct, ≤100 options, each ≤100 chars, non-blank; RELATION needs an existing target app (UUID) and takes no options; TEXT/NUMBER/DATE/USER take no config; FORMULA rejected outright (deferred, ROADMAP 3.0.B).
- Property type is IMMUTABLE on update (delete + recreate) — existing value rows would not convert.
- `deleteProperty` also bulk-deletes the value rows (dependent data, meaningless once the definition is gone).
- Position semantics: absent on create → append (max+1, first = 0); null on update → keep current (partial-PUT).
- `resolveProjectNames`: batched per page (one query), no per-row lookups.

### AppRecordService
- K-15 EAV path: `t_app_records` + `t_app_record_values(value jsonb)`. Record addressable only through its owning app (nested lookup, 404 on cross-app — same scoping as TaskService).
- Create: required coverage + per-PropertyType validation (`AppPropertyValueValidator`) + per-app plan soft-block.
- PATCH semantics: JSON `null` clears (rejected for required properties), absent keys keep.
- List/get/search responses bulk-fetch value rows (one query per page — no N+1); search delegates to the PG-only `AppRecordSearchExecutor`.

### AppQueryValidator
- Shared by record search (`AppRecordSearchRequest`) and saved view configs (`AppViewConfigDto`) so the two stay in lockstep.
- Eager validation (property existence, operator-vs-type, value shape) → invalid request is 400, never a mid-query 500. Downstream SQL references only validated UUIDs + enum fragments (injection-free).
- Operator/type matrix: TEXT = EQ/NOT_EQ/CONTAINS/IS_EMPTY/IS_NOT_EMPTY; NUMBER/DATE additionally GT/GTE/LT/LTE; SELECT/USER/RELATION = EQ/NOT_EQ/IS_EMPTY/IS_NOT_EMPTY; FORMULA = nothing (deferred). DATE compare takes ISO-8601 date strings. Reserved sort key `createdAt` addresses record creation time.
- FORMULA properties cannot be queried at all.

### AppRecordSearchExecutor
- K-15 / 3.0.B spike outcome: the filter/sort criteria ARE the query DSL — no expression language, no injection surface. SQL assembled exclusively from enum-derived fragments + explicitly numbered `?N` parameters.
- PG-only operators: `@>` containment (EQ), `#>> '{}'` text access, ILIKE (CONTAINS), `::numeric` casts for NUMBER compare/sort (NULLIF guards empty strings), GIN `jsonb_path_ops`-backed. DATE compares lexicographically as ISO text. Verified by gated `AppBuilderIT` (real PG); plain record CRUD stays portable under H2.
- Empty-cell semantics: a record with no value row for a property matches only IS_EMPTY/IS_NOT_EMPTY; value operators implicitly require a non-empty cell.
- Runs on the tenant's `search_path` through the multi-tenant EntityManager. `r.created_at DESC` tiebreaker keeps paging deterministic.
- Property-value sorts resolve through a correlated scalar subquery per sort clause.

### AppPropertyValueValidator
- USER/RELATION values get an existence check against tenant data — the JSONB column cannot carry an FK; same rationale as `Task.assigneeId` (plain column + service validation).
- TEXT ≤ 5000 chars (keeps JSONB rows small, rendering cheap); NUMBER must be finite; SELECT must be a configured option; DATE ISO-8601.
- SELECT config guard: a config-less SELECT was never creatable — a missing options array is a corrupt definition, fails loudly.
- `targetAppId` re-checked cheaply at value-validation time: definitions can drift (e.g. target app hard-purged) — a dangling relation must fail loudly.

### AppViewConfigValidator
- View-type anchors: BOARD requires `groupBy` (SELECT property), CALENDAR requires `dateProperty` (DATE property); TABLE/GALLERY/LIST carry neither (rejecting them if sent). Required anchors enforced even when the request carries NO config object at all (null config == empty config).
- Structured JSON only — the deliberate 3.0.B spike outcome: no free-text expression language → no expression-injection surface.

### AuditService
- K-19 layer 1: who (SecurityContext actor id+email), what (action/entity), request metadata (IP + traceId from `RequestContext`).
- REQUIRES_NEW so the write commits even if the audited op rolls back; best-effort (failure logged + swallowed — audit never breaks business). `recordInNewTx` invoked through the self proxy so the flush-at-commit lands inside `record`'s try/catch, not the caller's tx.
- No authenticated principal (startup/provisioning/background) → actor name `"system"`, actor id null.
- Old/new value capture = K-27 "Faz 2b": caller-built JSON (`namesJson` — sorted, escaped, dependency-free) answers "who granted/revoked which permission to whom".
- Test gotcha: REQUIRES_NEW writes commit outside a `@Transactional` test's rollback → assert membership with unique sentinels, never counts.

### AuditQueryService
- K-19 read side: paged views over `t_audit_logs` + `t_login_history`; controller guards with `iam:audit:read`; entity shape kept out of the API contract via response records.
- GET params translated into filter-engine `FilterCriteria` clauses and AND-combined through shared `FilterSpecifications` — no first-match dispatch, filters compose, engine exercised by real traffic.

### LoginHistoryService
- K-19 layer 2: EVERY login attempt recorded (success + failure); failure reason = stable `ErrorCode.code()` (`auth_bad_credentials`, `auth_account_locked`, ...) — uniform client response but stored reason enables brute-force/anomaly forensics (K-27).
- IP/User-Agent from `RequestContext`; null when absent (no web request — bootstrap/test).
- REQUIRES_NEW + best-effort: a failed login (`AuthService.login` throws) still records; a logging failure never breaks auth.

### RequestLogService
- K-19 layer 3 + K-27: `t_request_logs`; REQUIRES_NEW + best-effort.
- 42P01 (undefined table) swallowed quietly — tolerated when the request-log table does not exist yet in a schema.

### RequestLogQueryService
- K-19 layer 3 read side; `iam:audit:read` in the controller. `status` registered as INT (numeric compare, e.g. GTE 400); `requestBody` deliberately unregistered (masked high-risk payload, not a filter target).

### PlatformCompanyService
- K-25 cross-tenant ops on `public.t_companies`. `executeWithoutTenantContext` temporarily clears `TenantContext` — the ONLY sanctioned cross-tenant read path (RISK-18: `platform:*` currently seeded into every tenant's Admin; narrowing open); do not replicate elsewhere.
- `updateStatus` (RISK-32): illegal transitions rejected (e.g. TERMINATED→ACTIVE, ACTIVE→PROVISIONING) — they would leave the tenant in a broken state. Full billing-driven lifecycle in Faz 6.
- `mapToResponse` omits `schemaName` — internal detail, not API contract.

### PlanLimitService
- K-15: limit VALUES live in the code-side `PlanDefinition` registry; `t_plans` stores only reference data (key/rank) → limit changes ship with code, no migration. Enforcement = create-side soft-block (403 `app_limit_reached`); existing data never hidden/deleted.
- `tryActivePlan` = the single plan-resolution chain (K-40): Subscription → `t_plans.key` → registry. Empty in degraded states (no ACTIVE subscription / unknown key) — callers decide (ModuleActivationService shows it in the catalog but rejects activation).
- `activePlan` throws 409 `subscription_not_found` in every degraded state; no tenant context / unknown schema → `TenantNotFoundException`.

### ModuleActivationService
- K-16 flow order: plan gate → module tenant migration → permission seed → activation record LAST (every earlier step idempotent — Flyway history, ensure-permission; partial failure recovered by retrying, the DEBT-10 model).
- RISK-26 FK-deadlock avoidance: public-schema writes (checks, activation record) JOIN the caller's transaction — an activation triggered from provisioning must insert `t_tenant_modules` into the same tx holding the (not yet committed) `Company`, or the FK blocks on the uncommitted parent (self-deadlock on PostgreSQL). Only the permission seed runs REQUIRES_NEW — it writes the TENANT schema and the provisioning outer session is pinned to `public` (schema resolved at session open).
- `activateForCompany`/`resyncForCompany` wrap in a set-and-restore `TenantContext` window (`TenantContextExecutor`) so the REQUIRES_NEW seed + audit write resolve the tenant schema regardless of the caller's context.
- `listModules`: no-subscription tenants still get the catalog with `allowedByPlan=false` (activation itself rejects `subscription_not_found`).
- `ModuleProperties` optional: registered by `ModuleSyncRunner` (`@Profile("!test")`) — tests fall back to built-in default keys.
- K-45 `ensureDefaultProjectInNewTx`: ensures the module's per-type default "Genel"; migration already did it on PG (no-op), this covers re-activation after soft-delete + schemas where the module migration is a no-op. Content-collection types only (NOTES/APPS) — a TASKS default would be meaningless noise; an existing "Genel" of the type is adopted. Same REQUIRES_NEW isolation rationale.
- `resyncForCompany` (ModuleSyncRunner): newly shipped module migrations/permissions propagate to existing tenants; does not touch the activation record.

### TenantSampleDataService
- K-47 Linear-style onboarding: 1 TASKS project + 4 guided tasks, 2 categories + 2 markdown notes, 1 app + 3 properties + 2 views + 4 records (FREE plan limits respected). Fixed EN strings — tenant data, not UI; deliberately no i18n.
- Runs REQUIRES_NEW behind a set-and-restore window (RISK-26 — caller's session is public-pinned); invoked from provisioning's afterCommit so the fresh tx sees the committed activation + subscription rows (read-committed: a same-tx call fails those gates invisibly).
- Fail-safe: `seedForCompany` swallows every exception (warn log) — sample data must never break provisioning.
- Service reuse is safe: authority checks live in controllers (system context cannot 403); `@AuditLog` falls back to the `"system"` actor.
- Flat creates with null projectId land in the default "Genel" NOTES container; the 4th app record carries no stage value (the Board's empty-bucket example).
- Gated by `forgesys.provisioning.sample-data.enabled` (test: false); provisioning-only — existing tenants never touched.

### SubdomainSuggestionService
- K-21: normalize → Turkish-aware ASCII fold (ç/ğ/ı/I/İ/ö/ş/ü) → slugify → pattern validate → availability vs `t_companies`; `-2`/`-3` suffixes up to 3 candidates.
- `isValidSubdomain` keeps the provisioning service self-contained (DTO pattern already constrains requests).

### TenantMigrationSupport
- `migrateModule` (K-16): module migrations at `db/migration/module/{key}` against a module-scoped history table `flyway_schema_history_mod_<key>` — module versions never collide with core versions; each module versions independently from V1. Module locations deliberately OUTSIDE `db/migration/tenant` (recursive scan would swallow them into core history). `flywayLocation() == null` (tables ship in the core tenant baseline, e.g. pm) → no-op.
- `baselineOnMigrate(true)` + `baselineVersion("0")` for MODULE histories: the tenant schema is always non-empty at activation (core tables + core history exist) but the module history table does not exist yet — Flyway demands a baseline there. Baseline 0 records "nothing applied" and skips NOTHING (every module migration V1+ still runs). Contrast: the CORE history intentionally avoids baselineOnMigrate (K-36 — fresh-DB-only since the pre-1.0.0 squash; a baseline would silently skip the baseline family on a non-empty schema).

### List query executors (K-49 family)
- Shared pattern: one Criteria DTO projection (`cb.construct`, no entity hydration) + one count query with the same predicate; batched summary lists resolve in ONE extra query per kind per page. Flatness rule: JOINED targets to-one ONLY, SUBQUERY scalar (a to-many join would multiply rows and silently break paging).
- UserDirectoryQueryExecutor: replaced the former `@Immutable @Subselect` `UserDirectoryView` entity with an in-code projection the filter engine can filter/sort natively (joined columns, count subqueries, membership). Soft-delete semantics ride the joined entities' `@SQLRestriction` (applied to the LEFT JOIN ON) — role/group counts exclude soft-deleted rows.
- GroupListQueryExecutor: fixed 3-query page replacing per-row `findGroupMembers` + `countMembers` (2N+1). Member count starts from `User` (join table owned by `User.groups`). Former native count saw raw join rows; entity-path resolution now filters soft-deleted.
- RoleListQueryExecutor: `permissions`/`parents` stay batched lists (they carry descriptions/full summaries) rather than projection columns; the count subquery keeps `permissionCount` filterable/sortable in-DB.
- NoteListQueryExecutor: `referencedName` — correlated scalar subquery over a plain FK column (K-45 convention: notes/apps hold `categoryId`/`projectId` as UUIDs, not associations); `@SQLRestriction` applies inside the subquery → soft-deleted ref resolves null. `projectNameOf` reused by ProjectService (self-FK) and AppBuilderService.
- PlatformCompanyListQueryExecutor: replaced the unpaged `findAll()` (last K-37 paging violation); runs INSIDE `executeWithoutTenantContext` — cleared context pins the multi-tenant EM to the public schema.

### ProjectContainerSupport
- K-45 shared resolver: `assertProject` (404 unknown / 409 `project_type_mismatch`); `defaultProject` — per-tenant "Genel" ensured by the module V2 migrations + `ModuleActivationService`; shared by notes and apps modules.

### ProjectContentGuard
- K-45: while a container holds content of its current type (tasks pm / notes notes / apps apps — one check per content module), the type is locked (`project_type_change_forbidden`); mixed-content containers are the fragility this decision excludes.

### mail/* (K-21 replacement of `VerificationSender`)
- Port + profile split: `SmtpMailSender` (prod; provider-agnostic — Brevo/SendGrid/SES/plain SMTP all speak `spring.mail.*`; fail-fast on missing host at startup via `@PostConstruct`, fail-loud on send errors — a silently lost signup/reset link is worse than a retryable failure, the caller's tx rolls back; recipients never logged — PII). `LogMailSender` (dev; logs incl. the raw-token action URL — dev-only convenience, same trade-off as the former `LogVerificationSender`). `InMemoryMailSender` (test; `CopyOnWriteArrayList`, cleared per-test).
- Every message template-based (tenant signup, email verification, password reset) — the same channel carries all lifecycle mails.
- `MailLinkBuilder`: `{scheme}://{subdomain}.{host}[:{port}]{path}?token=...` derived from `forgesys.security.app-base-url` (the frontend origin) — the link lands on the tenant's own subdomain so `TenantFilter` resolves the schema when the browser POSTs the token back. Subdomain derived from `tenant_<sub>` (dashes folded to underscores at provisioning); the inverse fold is unique because subdomains reject underscores.
- `MailTemplate` enum: bodies at `mail/<key>.<lang>.html` (tr/en) outside the enum so non-developers can polish copy without a rebuild; subjects stable in code; `infra/templates/` (`templatesDir`) overrides classpath per template.
- `MailTemplateRenderer`: override file first, then classpath; a MISSING override falls through to classpath (a missing file must not silently disable a mail); placeholders are plain `{{token}}` string replacement — deliberately no expression language (template content can never execute code).
- `MailProperties`: `from` (RFC 822; fallback `ForgeSys <no-reply@forgessy.local>` — note the historical "forgessy" typo in the fallback), `defaultLanguage` (tr default until per-user preferences exist), `templatesDir`.
- `MailMessage`: the action URL token is RAW and must never be persisted by the sender (RISK-30).

### exception/*
- `ErrorCode`: wire value = lowercased enum name (`auth_bad_credentials`); clients branch on it — HTTP status and message may evolve, codes stay stable.
- `ApiErrorFactory`: traceId from MDC (populated per request by `RequestMetadataFilter`, honors `X-Request-Id` or generates a UUID); a fresh UUID outside a request thread so the field is never null.
- `BusinessException` hierarchy lives in backend (references Spring HTTP types — forbidden in the Spring-free `common` module); cross-module exceptions (e.g. `TenantNotFoundException`) stay plain `RuntimeException`s translated by the handler. Direct throws with a specific code (e.g. `USER_EMAIL_TAKEN`) are legal for one-off rules.
- `ApiErrorResponse`/`ApiFieldError`: uniform shape for controller advice + security entry point/access denied handler + tenant filter; sensitive rejected values sanitized before exposure.

---

## backend/security — backend/config

### SessionRevocationService
- Privilege-retention window rationale: authorities are embedded in the access token at
  issue time, so a role/permission/group mutation otherwise keeps affected tokens
  authoritative until the next issue (login/refresh) — up to the token TTL. The stamp +
  refresh-drop closes that window: revoked permission enforced on the very next request.
- Dropping refresh tokens matters because a stolen refresh could otherwise mint a fresh
  access token whose `iat` post-dates the revoke.
- Caller responsibilities split: `revokeRoleHolders`/`revokeGroupMembers` resolve *who is
  affected*; or explicit ids via `revokeUsers`.
- Bulk access-token stamp = one conditional UPDATE (`UserRepository.bulkSetTokenInvalidBefore`);
  refresh revoke is per-user (Redis) because the store is keyed per user.
- `revokeUsers`: refresh revoke only fires when a tenant is bound — the refresh store is
  tenant-scoped.
- `invalidateAccessTokens`: used by single-session admin revoke — the targeted device's own
  refresh token was already dropped, so the stamp signs it out on next request; siblings
  recover automatically via silent refresh (momentary blip, not a sign-out).
- `resolveRoleHolderIds`: post-soft-delete, `@SQLRestriction` hides the role and the lookup
  returns nobody; deferring revoke until after the last-admin guard means a rejected delete
  leaves no Redis-side revoke behind.
- `revokeAllPermissionsRoleHolders`: outstanding tokens embed the prior permission snapshot;
  all-permissions users should "hear about" a created/renamed permission rather than wait
  for TTL. No-op when no role carries the flag.
- `enforceSessionLimit` (Faz 5): implemented with the existing K-28 primitives
  (`RefreshTokenStore.listSessions`/`revokeSession`) — no store-contract change. Evicted
  device's short-lived access token is left to expire at TTL (session-cap eviction, NOT an
  admin remote-revoke — deliberately does not stamp `tokenInvalidBefore`). Login always
  succeeds; cap `<=0` = unlimited.

### JwtAuthenticationFilter
- Tenant binding (RISK-19) detail: token minted for tenant A replayed against tenant B =
  cross-tenant privilege escalation; rejected by clearing the context (→ 401). When the
  request carries no tenant (exempt/public paths), both sides normalize to `"public"`.
- Decode failure (bad signature/expired/malformed) → context cleared, request proceeds
  unauthenticated; protected routes get the uniform 401 from `RestAuthenticationEntryPoint`.
- `tokenInvalidBefore` is read via a single-column projection — no JOIN, no lazy proxy.
  Per-request cost: one small indexed query + one Redis lookup.
- Semantics of `isRevokedByTokenInvalidBefore`: narrow reject case = a present
  `tokenInvalidBefore` strictly after the token's issued-at second; absent row / null
  column / missing `iat` all accept. Full NumericDate-vs-timestamptz story: `iat` is
  seconds, the stamp keeps nanos; truncating the stamp to seconds means only a token
  whose iat SECOND is strictly earlier than the revoke second is rejected (protects
  fast re-login after password change).
- Token extraction order: cookie first (browser sessions), then `Authorization: Bearer`
  (API clients, cURL, jobs).

### CustomUserDetailsService
- `loadUserByUsername` is the `UserDetailsService` contract; `resolveAuthorities` is shared
  with `AuthService` at login.
- Why query-driven (history): the previous entity-graph walk depended on nested lazy
  collections (`group.roles` → `role.permissions` → `role.parentRoles`) initializing through
  `findByEmail` (no fetch graph) — fragile and an N+1 source; a full fetch graph is
  impossible (Hibernate multiple-bags limit). Explicit `UserRepository` queries sidestep both.
- Parent closure: BFS over `t_role_parents` until stable; the seed set doubles as the
  visited set so a malformed inheritance cycle (which `RoleService.setParents` already
  prevents) cannot infinite-loop.
- All-permissions short-circuit (checked AFTER the closure): built-in Admin (seeded with
  the flag) and any user-defined "ALL" role implicitly hold every permission, including
  runtime-created ones, with no `t_role_permissions` row per permission. Because the check
  is post-closure, a role that transitively inherits from an all-permissions role is itself
  treated as all-permissions.
- Permission wire format `{module}:{resource}:{action}`, sorted for stable display.
- `effectiveRoleIds` is the shared seed for both authority resolution and the
  effective-permissions view; inactive groups excluded so deactivation drops permissions.

### SecurityConfig (config/)
- Pepper rationale (K-23): a DB leak alone cannot brute-force hashes — the pepper lives
  outside the DB (env var / secret manager, `forgesys.security.password-pepper` /
  `PASSWORD_PEPPER`). BCrypt strength 12 (RISK-13). Legacy pepper-less hashes still
  validate and lazily rehash on next successful login. test/dev profiles ship a
  non-secret default; blank fails startup fast.
- `/api/v1/auth/**` public subset = tenant signup + login; everything else authenticated.
- K-41 full story: unconditional permitAll of `/v3/api-docs/**` + swagger is safe because
  prod disables springdoc outright (endpoints don't exist → 404); dev/test keep them for
  developers.
- K-43 full story: `/actuator/prometheus` unauthenticated in the dev/test same-port layout —
  carries only numerical system metrics (JVM/HTTP/tenant count), no PII/secrets. Prod moves
  management endpoints to their own port (`management.server.port`, application-prod.yaml)
  running WITHOUT this filter chain (child context) and never published off-host (compose
  exposes it on the internal network only) — the matcher is a no-op there.
- TenantFilter ordering story: runs before the security chain (security order -100) so
  tenant context is resolved for every request before the JWT auth filter executes; the
  explicit `FilterRegistrationBean` also suppresses the bare `@Component`'s default
  low-precedence auto-registration (same for RequestMetadataFilter).
- RequestLogFilter ordering story (regression history): the `t_request_logs` write happens
  in the filter's `finally`, which unwinds BEFORE the security/tenant/metadata filters
  clear their ThreadLocals — so tenant schema, authentication and request metadata are
  live at write time. Registered outside the chain on bare `@Order` once, the write landed
  in `public` with null user/trace, every insert failed and the swallowed 42P01 left the
  request-logs screen empty (regression-locked by `RequestLogFilterChainTest`).
- RequestMetadataFilter (-102): trace id, client IP and User-Agent available to every
  downstream filter/service; error responses carry a stable trace id.
- RequestBodyCaptureFilter (-94): wraps mutating high-risk requests with a cached body and
  publishes the masked body to `AuditRequestContext` BEFORE delegating, so both
  `AuditLogAspect` (during the request) and `RequestLogFilter` (in its finally) consume it.
- Filter order map: RequestMetadataFilter -102 → TenantFilter -101 → security chain -100 →
  RequestLogFilter -95 → RequestBodyCaptureFilter -94. RateLimitFilter sits before
  JwtAuthenticationFilter INSIDE the chain.

### RefreshTokenStore
- Tenant isolation: a session records its tenant schema; the caller validates session
  tenant vs request tenant so a token minted in tenant A cannot be refreshed in tenant B
  (mirrors RISK-19 access-token binding).
- Session model (K-28): stable `sessionId` + device metadata (IP / User-Agent / loginAt /
  lastSeen); `activeSessionFor` resolves the session behind a presented token so the
  service can flag the caller's current device. The store owns only refresh tokens —
  `SessionService` additionally stamps `tokenInvalidBefore` on revoke so the device's
  outstanding access token dies immediately, not at TTL.
- `listAllSessions` implementation: enumerates the tenant's per-user indexes (Redis SCAN /
  InMemory prefix scan), aggregates `listSessions` per user; bounded by the number of
  active refresh tokens (each expires); admin-only occasional read.

### RedisRefreshTokenStore
- Record contents: state/userId/email/tenant + K-28 session metadata
  (sessionId/ipAddress/userAgent/loginAt/lastSeen). Key formats:
  `refresh:tok:<sha256>`, index set `refresh:idx:<tenant>:<userId>` (backs
  `revokeAllForUser` + `listSessions`). TTL = refresh-token lifetime.
- Rotation Lua semantics: only an ACTIVE token flips to ROTATED and returns metadata;
  a ROTATED token reports REUSE — closes the read-modify-write race of two concurrent
  refreshes. Stable `sessionId` + original device metadata preserved; only `lastSeen`
  advances. Script returns flat list: {OK/NIL/REUSE, then payload fields}.
- RISK-36 full story: `issue` is the only fail-closed path — a token that could not be
  stored must not be handed out (exception propagates → 503 `service_unavailable`).
  Rotate degrades to `Unknown` (clean 401, no 500); session list/revoke reads return
  empty/false best-effort. Recovery automatic once Redis returns.
- `revoke` chain-following: a ROTATED record's `rotatedTo` successor is the live token of
  the SAME session (rotation preserves sessionId); not killing the successor would leave
  the session surviving logout and showing ACTIVE to admins.

### InMemoryRefreshTokenStore
- Mirrors the Redis state machine (ACTIVE→ROTATED, reuse detection, per-user index) in
  plain concurrent maps so the default H2 suite exercises rotation/reuse and session
  listing/revoke without a Redis container.
- TTL/expiry deliberately not enforced here — verified against real Redis by the gated
  `RedisRefreshTokenIT` (`-Dforgesys.redis.it=true`).

### LastAdminGuard
- Root cause closed: an admin could soft-delete/disable themselves, strip their own admin
  role, or delete/degrade the last admin-capable role/group → tenant with zero
  admin-capable users, no in-product recovery (platform-level rescue is deliberate future
  work) — prevention is the only defense.
- Admin-capable definition mirrors `CustomUserDetailsService` authority resolution with the
  direction reversed: flag roles are expanded DOWNWARD through `t_role_parents` (children
  of an admin role are admin-capable) before checking for holders. "Admin" the NAME is
  only a seed convention; a specific permission is NOT the test.
- Invariant detail: non-deleted AND `enabled=true` (`@SQLRestriction` hides soft-deleted
  users; disabled admins don't count).
- Usage mechanics: the existence query is JPQL → Hibernate auto-flushes pending entity
  changes (removed role/group join rows, `enabled=false`, soft-delete UPDATE) before it
  runs; violation throws `LAST_ADMIN_REQUIRED` (409) rolling the whole mutation back.
- `assertNotSelf` throws `SELF_DELETE_FORBIDDEN` (409) when target matches the
  authenticated principal; self-delete is never necessary.

### PepperingPasswordEncoder
- OWASP-recommended pepper strategy for BCrypt (BCrypt has no native pepper). 32-byte
  HMAC → 44 Base64 chars, well under BCrypt's 72-byte input limit.
- Hash formats: legacy pepper-less `$2a$12$...` from the pre-K-23 `BCryptPasswordEncoder(12)`
  bean (RISK-13 lineage); peppered `{sf-peppered}$2a$12$...`. `matches` detects the marker;
  `upgradeEncoding` true on legacy → login flow rehashes (lazy migration, same philosophy
  as RISK-13 strength upgrades).
- Pepper rotation deliberately unsupported: a change invalidates every peppered hash
  (legacy hashes would still verify then rehash to the new pepper); a dedicated migration
  flow would be needed.
- Pepper must be non-blank (Assert); held outside the DB.

### JwtTokenProvider
- Claim set: sub=userId, jti (unique per token — granular blacklist target, K-34),
  email, tenant (schema), authorities (resolved permission strings), iss/iat/exp.
- Tenant claim omitted keeps the builder happy when no tenant was resolved (login always
  resolves one via subdomain).

### JwtConfig
- Shared KeyPair bean so encoder and decoder always agree; RsaKeyProperties +
  JwtCookieProperties enabled here.
- RISK-14: resource-server auto-config filter NOT enabled — the custom
  `JwtAuthenticationFilter` handles decoding + context population so revocation
  (`tokenInvalidBefore` / Redis blacklist) could be layered on.

### JwtCookieProperties
- Property list: cookieName / refreshCookieName (keys); cookieSecure / refreshCookieSecure
  (dev/test false HTTP; prod forced true by application-prod.yaml, RISK-24 — cleartext
  leakage over HTTP downgrade / mixed content); cookieSameSite (default Lax);
  refreshTokenTtlDays (default 7 — drives Redis TTL + refresh-cookie Max-Age);
  refreshCookiePath (default `/api/v1/auth` so the cookie is only sent to auth endpoints).
- `jwt.*` prefix; the `jwt.rsa.*` subkeys are owned by `RsaKeyProperties` and ignored here
  (record has no matching field).
- K-40: the build/expire/read helpers are the single cookie construction path shared by
  the auth and session controllers — controllers never assemble `ResponseCookie`s
  themselves. Resolved-with-defaults so callers/tests never see nulls.

### RsaKeys
- `resolve(failIfUnconfigured)`: true (prod) throws IllegalStateException fail-fast — an
  unconfigured prod deployment must not silently start on an ephemeral key (tokens
  wouldn't survive restart; multi-instance clusters would each mint under different
  keys → random 401s) [RISK-23]. False (dev/test) generates ephemeral 2048-bit with a
  warning so local dev/tests need no cert files.

### RsaKeyProperties
- Loaded from externalized config (e.g. `certs/*.pem` in prod); when neither PEM is set,
  `JwtConfig` falls back through `RsaKeys.resolve`. Keys NEVER committed (AGENTS
  "Never" rule).

### TokenHasher
- Raw token only ever lives in its delivery channel (email link, cookie, response body);
  every persisted form (DB column or Redis key/value) carries the digest, so a store/backup
  leak cannot replay it (K-34 refresh + RISK-30 verification tokens). Lowercase hex matches
  PostgreSQL `encode(sha256(x), 'hex')` (public V3 backfill); replaced former per-store
  private copies.

### TokenBlacklistService
- On per-session logout the current access token's `jti` is blacklisted with TTL = token's
  remaining lifetime; `JwtAuthenticationFilter` rejects it (→ 401) without waiting for
  expiry. Complements user-scoped `tokenInvalidBefore`.

### RedisTokenBlacklistService
- Fail-open reasoning: blacklist is defense-in-depth on top of signature + expiry +
  `tokenInvalidBefore` (RISK-21); a skipped blacklist write simply expires with the
  token's TTL; a failed read's exposure window is bounded by the short access-token
  lifetime. A Redis blip must not 500 every authenticated request or break logout.

### InMemoryTokenBlacklistService
- `jti → expiry-millis`, pruned on read — enough for the H2 suite to exercise per-session
  logout without a Redis container.

### CustomUserDetails
- Two construction paths: `CustomUserDetailsService` from the DB at login (real account
  flags + resolved authorities); `JwtAuthenticationFilter` from claims per request
  (account flags assumed valid for the short-lived token).
- RISK-22 full story: the lockout writes `lockedUntil` only; `accountNonLocked` column
  stays true. Without the effective-locked check, a locked account with a live refresh
  token could keep minting fresh access tokens via `/auth/refresh` for the whole lock
  duration (access tokens are killed via `tokenInvalidBefore`, but refresh immediately
  reissues). Lock expiry lazy: past `lockedUntil` counts non-locked; login clears it.
- `getJti` used by per-session logout to blacklist the single token; null on login-time
  principals (before a token exists).

### RateLimitFilter
- Closes the credential-stuffing paths the per-account lockout (RISK-22) misses: one IP
  guessing across many accounts, and unknown-email attempts that never increment any
  account's lockout counter.
- Registered inside the Spring Security chain before `JwtAuthenticationFilter` so a
  blocked request never reaches the controller. Scope derived from the request path
  (login / company-verify / verify-email / forgot-password / reset-password / refresh).
- Master switch `forgesys.security.rate-limit.enabled` disables wholesale.

### RateLimiter
- Bucket key example: `rl:login:tenant_acme:10.0.0.1`. Capacity = burst, then steady
  refill. Params per call so one limiter serves endpoint profiles with different limits.

### RateLimitProperties
- `enabled` also usable to bypass in tests that hammer auth endpoints. Stricter
  per-endpoint profiles are a K-XX follow-up; the uniform profile closes the
  credential-stuffing gap at the request edge.

### RedisRateLimiter
- Lua script mirrors the refresh-rotation Lua pattern in `RedisRefreshTokenStore`; closes
  the read-modify-write race two concurrent requests from the same key would otherwise open.
- Bucket TTL 600s (slightly above the refill window) so idle keys expire.

### InMemoryRateLimiter
- ConcurrentHashMap of `[tokens, lastRefillEpochSec]` pairs; refill/consume math identical
  to the Lua script; real Redis atomicity verified on the dev/prod path.

### RestAuthenticationEntryPoint / RestAccessDeniedHandler
- Replace Spring Security defaults (login redirect / 403) with the uniform
  `ApiErrorResponse` shape (401 `auth_unauthenticated` / 403 `auth_access_denied`).

### ActiveSession / RefreshSession / RotationResult / IssuedRefresh (refresh/)
- ActiveSession vs RefreshSession: the lean RefreshSession carries only what
  `AuthService` needs to re-resolve a user on rotation; ActiveSession carries device
  metadata for the UI list. Param semantics: sessionId stable per device (preserved
  across rotation); loginAt = first issue (preserved); lastSeen = most recent rotation
  (equals loginAt until rotated); ipAddress/userAgent nullable (tests/unknown). `current`
  flag set by the service layer matching the caller's presented refresh token.
- RotationResult is sealed so callers handle every case; ReuseDetected caller duty:
  `revokeAllForUser` + stamp `tokenInvalidBefore` so outstanding access tokens die too.
- IssuedRefresh: raw opaque token (URL-safe Base64, 32 bytes entropy) handed to the
  client via cookie/body; store keeps only the SHA-256 hash.

### ModuleDefinition (config/)
- Replaces a `t_module_catalog` DB table ON PURPOSE: a module is code (entities, services,
  migrations) so the registry entry must ship with the code and cannot drift.
- Field semantics: `ownMigrations=false` means tables already ship in the core tenant
  baseline (true for `pm`, whose tables predate the module system); `projectType` (K-45)
  — the creatable type catalog derives from the tenant's ACTIVE modules, null = no
  container-facing type; `permissions` seeded into `t_permissions` on activation and
  re-synced at startup for activated modules.
- APPS (K-15 / Epic 3.0.B): first ownMigrations=true module; adoption is the point of
  minPlan=FREE.
- NOTES (K-44 / Epic 3.2): per-module history `flyway_schema_history_mod_notes`; no plan
  limits (pm convention); `notes:note:read` sees all tenant notes.

### RbacSeeder (config/)
- Runs at startup iterating `t_companies`, switching TenantContext per tenant (mirrors
  TenantMigrationRunner); also invoked by `TenantProvisioningService.createAdminUser` so a
  brand-new tenant is seed-complete before the request returns. Disabled in test (fixtures
  built manually).
- Privilege-escalation fix history (2026-08-16): auto-assigning Admin to role-less users
  silently elevated deliberately unprivileged users to full admin on EVERY restart.
- `seedForCurrentTenant` is @Transactional and called through the Spring proxy
  (ObjectProvider self-proxy) from `run` and from provisioning — keeps the session open
  for lazy collection initialization (`Role.permissions`, `User.roles`).
- Admin role description: "Full administrative access (implicit all-permissions role)";
  all_permissions resolved dynamically by CustomUserDetailsService.

### PlanSyncRunner (config/)
- K-16 / Epic 3.0.A; disabled in test (tests build plan fixtures manually). Order 0
  because SystemAdminBootstrapRunner (tenant provisioning writes a subscription) and
  ModuleSyncRunner (subscription backfill) depend on the plan rows existing.

### ModuleSyncRunner (config/)
- FREE backfill preserves the pre-3.0.A behavior where every tenant had every module;
  default keys ensured ACTIVE idempotently (pm backfills every existing tenant); re-sync
  propagates newly shipped module migrations/permissions to existing tenants. Disabled in
  test (ModuleActivationService falls back to built-in default keys there).

### SystemAdminBootstrapRunner / SystemAdminBootstrapProperties (config/) — K-50 ile KALDIRILDI
- K-24: gave the platform a stable privileged identity without manual signup; used for platform operations and as a service account for M2M/job outbound calls. The RBAC seed and the explicit Admin grant happened inside `createAdminUser` during provisioning — the runner intentionally only performed tenant + admin provisioning. Disabled in test.
- `provisionSystemTenant` = createPendingCompany + verifyAndProvision back-to-back (K-21 two-phase flow) with verification mail suppressed.
- **K-50 ile kaldırıldı** (2026-08-26): `SystemAdminBootstrapRunner` + `SystemAdminBootstrapProperties` + `provisionSystemTenant` silindi; mevcut `system` tenant DB satırı dokunulmadan bırakıldı. Yerine platform kimlikleri (`public` şeması) + `PlatformAdminBootstrapRunner` (K-50).

### SchedulingConfig (config/)
- First @Scheduled consumer: TokenPurgeJob (RISK-30 stale verification-token purge).

### TokenPurgeJob (config/)
- Purges both token families: public `t_tenant_verification_tokens` + per-tenant
  `t_auth_tokens` (email verify / password reset). Cutoff columns `used_at`/`expires_at`.
- Signup purge runs through the `self` ObjectProvider proxy (same pattern as
  TenantProvisioningService.createAdminUser): @Scheduled entry point and transactional
  worker in one class without a self-invocation trap.

### OpenApiConfig (config/)
- Spec at /v3/api-docs, UI at /swagger-ui.html in dev/test. The httpOnly cookie is
  attached automatically by the browser; JS never reads it. To try authenticated
  endpoints from Swagger UI: call /auth/login first in the same browser session
  (cookies flow same-origin) — or use a tenant subdomain host plus the dev-only
  X-Tenant-ID header when hitting localhost directly.

### CorsConfig (config/)
- Origins comma-separated via `forgesys.security.cors.allowed-origins`; default covers the
  Vite dev server (5173 + 3000 in yaml).

### MultiTenancyJpaConfig (config/)
- AuditorAware closes RISK-33/RISK-3: created_by/updated_by = authenticated user id,
  "system" fallback when unauthenticated (signup, provisioning, startup runners).
- DateTimeProvider returns UTC OffsetDateTime (RISK-15).

### SampleDataConfig / SampleDataProperties (config/)
- K-47: unlike runner-bound properties (ModuleProperties is registered by a !test
  runner), TenantSampleDataService is a plain service whose bean exists in the test
  context too — hence registered in EVERY profile; the test yaml flips the flag to false.
  A disabled flag simply leaves new tenants empty.

### ModuleProperties (config/)
- Default-keys semantics: activated at provisioning + backfilled at startup for
  pre-module-system tenants; unknown keys logged + skipped by consumers.

### TenantMetrics (config/)
- JVM/HTTP/system series come from Micrometer auto-configured binders; active tenant
  count is the only business number meaningful per-process. Per-tenant gauges would need
  a labeled push design, not a scrape-time gauge. One lightweight K-40 TenantSchemaView
  projection query (no entity hydration) per scrape.

### PermissionCatalog (config/)
- Names follow `{module}:{resource}:{action}`. iam:* enforced by @PreAuthorize on RBAC
  controllers; platform:* reserved for the system tenant admin on `/api/v1/platform/**`.
- CORE drives only the always-present rows in t_permissions; module permissions reach the
  Admin role automatically once seeded on activation (all_permissions).

### PlanDefinition (config/)
- Until Faz 6 the t_plans rows are reference data gating module activation only.
- FREE=0 / PRO=1 / ENTERPRISE=2; limits: maxApps 3/25/-1, maxRecordsPerApp 1k/50k/-1.

> **Kayda değer bulunmayıp yalnızca silinenler** (security/config taraması): runner
> javadoc'larındaki tekrarlı "startup'ta iterates/try-catch" kalıpları; record `@param`
> tekrarları; cron'u tekrar eden "03:00 off-peak"; eski faz konuşması ("Chunk C/D").

---

## backend/web — backend/audit — controller/dto/tenant

### FilterFieldSet

- K-49 filter engine: per-feature whitelist of filterable/sortable attributes.
  Only declared fields are reachable from the wire — the defense against
  filtering through relations or leaking internal fields. Wire names stay FLAT
  (`a.b` nested paths are structurally unreachable; SortGuard rejects them).
- Registration uses JPA canonical metamodel String constants (e.g.
  `User_.EMAIL`): a renamed entity field breaks the build instead of silently
  opening a stale/absent field.
- Same set doubles as the sort whitelist (`SortGuard.require(pageable, fields)`)
  — one source of truth per feature.
- Four registration kinds:
  - DIRECT — attribute of the root entity (wire name == attribute name).
  - JOINED — attribute of a to-one LEFT JOIN of the root (e.g. user's
    `firstName` via `userProfile`). To-many not registrable by design.
  - SUBQUERY — derived scalar (counts over plural associations, name-resolution
    through plain FK UUID columns) via `SubqueryExpression`; used in WHERE,
    ORDER BY and SELECT; must stay scalar.
  - MEMBERSHIP — collection contains (`roleIds IN [...]`, `IS_NULL` = empty
    collection) via correlated EXISTS; filter-only, never sortable, values are
    member ids. Two forms: `direct` (correlates a plural association of the
    root) and `inverse` (join table owned by the other side, e.g. a group's
    members live in `User.groups`; EXISTS starts from the member entity and
    correlates back through its association by link id).
- Flatness rule: JOINED only to-one, SUBQUERY scalar — a to-many join would
  multiply rows and silently break paging counts. Collection data goes through
  SUBQUERY/MEMBERSHIP.
- Builder invariants: searchable fields must be STRING-typed (q is a
  case-insensitive containment over text).

### FilterSpecifications

- Translates global `q` (optionally narrowed to `qFields`) + `FilterCriteria`
  clauses into one Specification: filters AND-joined; `q` OR-CONTAINS over
  registered searchable fields (or the selected subset when `qFields` given).
- All validation is eager at build time — invalid request fails 400
  `validation_error` before any query runs, never a mid-execution 500.
- `MAX_IN_VALUES = 100` — bounds generated SQL for IN/NOT_IN clauses.
- Arity: EQ/NOT_EQ/GT/GTE/LT/LTE/CONTAINS/STARTS_WITH/ENDS_WITH exactly 1,
  BETWEEN exactly 2, IN/NOT_IN 1..100, IS_NULL/IS_NOT_NULL 0.
- Membership predicates: correlated EXISTS; direct form correlates the root
  association, inverse form starts from the member entity. Correlated joins
  apply the member entity's `@SQLRestriction`, so soft-deleted members are
  excluded — semantics match what the associations resolve to elsewhere.
- `likeIgnoreCase`: column lowered by DB, pattern lowered with
  `Locale.ROOT` in Java. Assumes DB lower() folds ASCII the same way — true for
  en-locale PostgreSQL and for the H2 test JVM (surefire `user.language=en`
  argLine pin; a Turkish-locale JVM would turn `lower('I')` into `'ı'` and
  break case-insensitive search on H2).
- `escapeLike` escapes `% _ \` so user input matches literally (escape char
  passed to `cb.like`).
- `comparablePredicate`: the unchecked casts pin one `Y` for the whole
  expression/value pair, resolving the CriteriaBuilder overload ambiguity;
  values were parsed to the field's declared type by FilterValueParser, so
  erasure keeps them type-correct at runtime.

### FilterValueParser

- Parses wire strings per the field's `FilterFieldType` (STRING / UUID /
  BOOLEAN / TEMPORAL (OffsetDateTime) / DATE (LocalDate) / NUMERIC (Long) /
  INT (Integer) / ENUM (Enum.valueOf)).
- Every parse failure is 400 `validation_error` naming the field — an
  unparseable value must never surface as 500 or silently match nothing
  ([RISK-29] semantics).

### FilterOperator

- 14 operators: EQ, NOT_EQ, IN, NOT_IN, CONTAINS, STARTS_WITH, ENDS_WITH, GT,
  GTE, LT, LTE, BETWEEN, IS_NULL, IS_NOT_NULL. Wire value = enum name.
- `TRUE`/`FALSE` pseudo-operators deliberately absent — EQ on a boolean covers
  them with one code path.

### FilterFieldType

- Kept intentionally small; new kinds arrived with the first attribute that
  needed them: NUMERIC with `RequestLog.durationMs`, DATE with `Task.dueDate`,
  INT with `RequestLog.status`. ENUM carries `null` default java type (per-
  field enum class supplied at registration; unknown names → 400, not silently
  unmatched).

### SearchRequests

- Maps a SearchRequest body onto a Pageable and runs the sort through
  SortGuard against the feature's FilterFieldSet — POST /search bodies get the
  same whitelist treatment as GET lists, so no property-path injection path
  exists on either surface. Page/size bounds enforced upstream by Bean
  Validation on the DTO. DEFAULT_SIZE = 20 (matches the GET default).

### SortGuard

- Without a whitelist a client can order by any resolvable property path
  (e.g. nested `userAccount.tokenInvalidBefore`) — leaks internal model shape
  AND bypasses the unknown-property `PropertyReferenceException` guard (a
  nested path that EXISTS resolves fine at the repository layer).
- Whitelist entries are metamodel constants (`User_.EMAIL`,
  `AuditEntity_.CREATED_DATE`) — renames break the build, not the wire.
- Order-insensitive; violation → 400 `validation_error` listing the sortable
  names (membership fields are never sortable via the `sortable` flag).

### ProjectionListQuery

- K-49 shared Criteria DTO projection executor: one content query
  (`cb.construct` — rows are DTOs, never managed entities: no hydration
  overhead, no N+1, no dirty checking) + one count query with the same
  predicate. Sort properties resolve through the feature's FilterFieldSet so
  joined/subquery columns sort in the DB like direct ones.
- Flatness rule: registrations may JOIN only to-one (LEFT) and keep subquery
  fields scalar — a to-many join would multiply rows and silently break both
  paging and the count query. Collection data goes through scalar subqueries /
  EXISTS (mirrors the former `UserDirectoryView` read model).
- SortGuard runs at the controller layer; a non-registered/non-sortable
  property here throws IllegalArgumentException as the last line of defense.

### RequestMetadataFilter

- Order -102 (tenant -101, security -100): runs before everything so error
  responses produced downstream carry a stable trace id. No `shouldNotFilter`
  — metadata is useful for every path incl. actuator/public auth.
- Trace id: `X-Request-Id` header when present AND matching
  `^[A-Za-z0-9._-]{1,128}$` (bounded/safe charset prevents MDC/log forging);
  otherwise a fresh UUID.
- Client IP: `X-Forwarded-For` first hop → `X-Real-IP` → `getRemoteAddr()`.
  Forwarded headers trusted because prod runs behind a trusted reverse proxy
  (K-33 topology).
- User-Agent truncated to 500 chars (matches `t_login_history.user_agent` /
  adjacent audit column limits).
- RequestContext + MDC cleared in `finally` — no ThreadLocal leak across
  reused request threads.

### RequestBodyCaptureFilter

- Order -94, registered INSIDE the security chain after RequestLogFilter
  (-95). Wraps mutating requests (POST/PUT/PATCH/DELETE) on high-risk paths
  with a cached body and publishes the MASKED body to AuditRequestContext
  BEFORE delegating — AuditLogAspect peeks it during the request and
  RequestLogFilter consumes it in its finally (the single clear point).
- Default high-risk paths (`forgesys.audit.high-risk-paths`):
  /api/v1/users/**, /roles/**, /groups/**, /permissions/**, /platform/**,
  /modules/**, /apps/**.
- Default mask key substrings (`forgesys.audit.mask-patterns`, lowercase,
  substring match): password, token, secret, credential, authorization,
  apiKey, accessKey, clientSecret → value replaced with `[REDACTED]`.
  Masking recurses into nested maps and lists; unparseable body →
  `[MASKING_FAILED]`; body fully read at wrap time so masking needs no
  response data.

### RequestLogFilter

- Order -95, INSIDE the security chain (after the security
  DelegatingFilterProxy at -100): the write happens in this filter's finally,
  which unwinds BEFORE the security/tenant/metadata filters clear their
  ThreadLocals — tenant schema, authentication and request metadata are still
  live when the `t_request_logs` row is written. (History: bare
  `@Order(HIGHEST_PRECEDENCE + n)` once placed these filters outermost; the
  write landed in `public` with null user/trace and the swallowed 42P01 left
  the request-logs screen empty — regression-locked by
  RequestLogFilterChainTest. Do NOT move them outside the chain.)
- Skips the write when no tenant was resolved (actuator, tenant signup,
  unknown host): `t_request_logs` exists only in tenant schemas, an insert
  would land in `public` and fail.
- Single clear point for AuditRequestContext — the masked body never leaks to
  the next request on a reused thread.
- Requests rejected inside the security chain itself (401 before -95) are not
  logged — failed logins are covered by `t_login_history`.

### RequestContext / RequestMeta / AuditRequestContext

- RequestContext mirrors the common `TenantContext` ThreadLocal pattern but
  lives in the backend module: only the web layer writes it, only backend
  services read it. Common-module rule — a type belongs in `common` only when
  shared by more than one module.
- Only the trace id is logged; client IP / User-Agent are PII and never logged.
- AuditRequestContext: body is PEEKED by AuditLogAspect (get, not
  get-and-clear — consuming there would leave the request-log row without its
  body; the value must survive until RequestLogFilter's finally).

### AuditLogAspect

- `@Around` on `@AuditLog`: proceeds first (no audit when the business op
  throws), builds a SpEL StandardEvaluationContext (method params by name +
  `#result`), evaluates entityId/entityName, delegates to AuditService through
  an ObjectProvider self-proxy so the REQUIRES_NEW boundary is honored.
- Audit failures are caught and logged at warn — audit logging never breaks
  the business op.
- Test hook (`setTestHook`/`clearTestHook`) lets unit tests capture audit calls
  without a Spring context.

### AuditLog (annotation)

- Usage example:
  `@AuditLog(action = "user_created", entityType = "User", entityId = "#result.id", entityName = "#result.email")`
- `captureDelta = true` for privilege changes (role/group/permission
  assignments): `oldValue`/`newValue` SpEL expressions evaluate to before/after
  name collections (e.g. `#beforeRoleNames` / `#afterRoleNames`); the caller
  must place those variables in the evaluation context.

### Controllers (shared pattern)

- Every list controller repeats: GET list (bookmarkable `?q=`/`?qFields=` +
  Spring Data sort through SortGuard) + `POST /{resource}/search` (full
  SearchRequest filter-engine body, same read permission). The 1-line
  "filter-engine variant" javadoc on each /search method refers to this.
- AuthController: register returns 202 (PROVISIONING company — resource not
  ready, not 201); verify is the synchronous heavy phase (CREATE SCHEMA +
  Flyway + admin user); forgot-password ALWAYS 200 (no enumeration); logout =
  per-session (consume refresh + blacklist jti; user-scoped
  tokenInvalidBefore reserved for password change/reset/reuse).
- UserProfileController: literal `me` segment takes precedence over the
  `/{id}` variable in UserController (Spring MVC literal-beats-variable).
- UserSessionController: self scope (`/users/me/sessions`, any authenticated
  user; current device flagged via the `sf_refresh_token` cookie) vs admin
  scope (`/users/{id}/sessions`, `iam:user:write`, remote revoke). Ending a
  session drops the refresh token and stamps `tokenInvalidBefore` so the
  device's outstanding access token dies on its next request rather than at
  TTL. Self-revoke of the current device expires BOTH cookies for an instant
  logout. Tenant-wide view: SessionController (`GET /api/v1/sessions`); revoke
  reuses `DELETE /api/v1/users/{id}/sessions/{sessionId}`.
- AppController `/plan-limits` is declared before `/{id}` only for readability
  — Spring MVC gives literal segments precedence over path variables
  regardless of declaration order.
- AuditController: GET params (action/actorId, userId/success,
  traceId/method/status/userId/username) are translated into engine criteria
  and AND-combined by the query services; each surface also has the full POST
  /search variant. `iam:audit:read` is seeded into the Admin role.

### TenantFilter

- `shouldNotFilter` exempts ONLY `/api/v1/auth/company/**` (tenant creation —
  no tenant yet) and `/actuator/**`. Login and /me ARE tenant-specific and go
  through normal subdomain resolution.
- Only ACTIVE companies resolve (PROVISIONING/SUSPENDED/TERMINATED do not).
- `X-Tenant-ID` header fallback is dev-profile only — fully disabled in prod.

### TenantContextExecutor

- Set-and-restore window; the single sanctioned way to switch tenant context
  programmatically. Hand-rolled set/clear pairs leak the tenant across pooled
  threads when the restore/clear is missed ([RISK-10]). Restores the caller's
  context; clears when there was none.

### DTOs (wire contracts worth keeping)

- AssignPermissionsRequest: two modes — explicit (`all` null/false: replace
  set with `permissionIds`, empty clears all, unresolvable ids 404) vs
  `all=true` (set `all_permissions` flag, ignore `permissionIds`, clear the
  explicit set — the "ALL" shortcut). Switching back to explicit clears the
  flag; `permissionIds` must then be present. Max 100 ids.
- SearchRequest: page ≥0, size 1..1000 (aligned with
  `spring.data.web.pageable.max-page-size`), ≤5 sorts, ≤10 filters, q ≤200
  chars, ≤10 qFields. "A bounded request is a cheap request."
- PageResponse: `{data[], meta{page(0-based), pageSize, totalElements,
  totalPages, hasNext, hasPrevious}}` — API owns the wire contract, not
  Spring Data's drifting Page serialization.
- LoginResponse: access token also httpOnly cookie `sf_access_token`, refresh
  `sf_refresh_token`; body copies serve non-browser clients; `refreshToken`
  null on the /me shape; authorities are `{module}:{resource}:{action}`.
- ActiveSessionResponse vs AdminSessionResponse: admin view carries owner
  userId+email; `current` always false/absent on the admin view (admin is not
  the session owner).
- AppPropertyRequest: `type` immutable after creation (existing values would
  be meaningless after a change); `required` keeps its wrapper +
  compact-constructor default because Jackson 3 fails null-into-primitive
  mapping for absent fields.
- AppViewConfigDto: deliberately a STRUCTURED shape, not a free-text
  expression language — injection surface is structural (3.0.B spike
  outcome); every field resolves to a property id or enum; backend
  re-validates against the app's property set before persisting.
- AppRecordSearchRequest: JSONB EAV path (PostgreSQL `@>` containment / `#>>`
  accessors, GIN-backed); limits ≤10 filters / ≤5 sorts / size ≤100 (value
  scans heavier than column reads → tighter page cap than the generic engine).
- UserDirectoryViewResponse: flat list projection; association lists become
  counts; detail endpoint still returns full role/group sets.
- NoteRequest/AppRequest: create without `projectId` → default container;
  update `null` = leave unchanged, value = move.
- NoteCategoryRequest: `color` is a UI token never interpreted server-side; a
  category's project is fixed at create (moves rejected 409).
- SubdomainRules: `^[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?$` — single source
  shared by CompanyRegisterRequest @Pattern and SubdomainSuggestionService so
  the two cannot drift.
- AppPlanLimitsResponse: values from the PlanDefinition registry via
  PlanLimitService.activePlan() (single plan-resolution chain); -1 =
  unlimited.

---

## persistence

### `UserRepository` — yetki çözümleme & oturum iptal sorguları

- **Neden JPQL id/name projeksiyonları** (`findDirectRoleIds` → `findActiveGroupRoleIds` →
  `findParentRoleIds` → `findPermissionNamesByRoleIds`): `CustomUserDetailsService` bu zinciri
  iteratif yürütür (parent kapanışı BFS + visited set). Tam grafik
  (`groups.roles.permissions` + `roles.parentRoles`) TEK `@EntityGraph`'te çekilemez —
  Hibernate **multiple-bags** limiti; lazy N+1 da istenmez. İd/name projeksiyonları iki
  kısıtı da aşar.
- **`findAll`/`findById` `@EntityGraph` override'ları**: liste ve detay okumaları
  `UserResponse` kurarken profile/account/roles/groups'e dokunur — graphsız her erişim
  tenant şemasında ekstra SELECT üretir. `roles`/`groups` `Set` (bag değil) olduğundan
  iki koleksiyon + iki `@OneToOne` aynı grafta güvenli (yine multiple-bags).
- **`findTokenInvalidBefore` tek-kolon projeksiyonu**: `UserAccount` kullanıcının PK'sını
  `@MapsId` ile paylaşır → JOIN'siz, lazy `@OneToOne` proxy'siz tek satır/kolon. Kullanıcı
  veya hesap satırı yoksa boş döner (silinmiş hesap / bilinmeyen subject) — filtre boş
  JWT'yi reddetmez, "iptal kaydı yok" kabul eder.
- **`findUsersByRole` neden entity döner**: rol soft-delete'ten ÖNCE join satırlarının
  yönetilen `User.roles` koleksiyonlarından koparılması gerekir; satırlar yerinde
  kalırsa flush `TransientPropertyValueException` ile patlar.
- **`bulkSetTokenInvalidBefore` `flushAutomatically = true`**: iptali tetikleyen bekleyen
  entity değişiklikleri (rol/grup/şifre mutasyonu) UPDATE'ten ÖNCE flush edilir; `a.id in
  :userIds` doğrudan kullanıcı PK'sıdır (`@MapsId`). "Etkilenen herkes" kümesi: rolü
  direkt tutanlar + AKTİF grup üzerinden tutanlar (pasif grup anında yetim bırakır).
- **`existsEnabledByRoleIds`**: "en az bir aktif admin kalsın" (LastAdminGuard) —
  soft-delete'liler `@SQLRestriction` ile gizli, disabled hesap admin sayılmaz.
- **Görünürlük kapsamı** (`findGroupIdsByUserId` + `findUserIdsByGroupIds`):
  `iam:group-member:read` — çağıran, kendi gruplarının üyelerini + kendisini görür;
  grubu olmayan kullanıcı da kendini görsün diye kendi id'sini çağıran ekler.


### components/ui/DataTable.tsx (K-37/K-49-adjacent)

- `sortKey` MUST be in the backend feature's sort whitelist (SortGuard) or the
  request 400s — composite columns sort by a different field than `key`
  (users "name" column sorts by `email`).
- `filter` + `filters`/`onFiltersChange` are the K-49 column-filter opt-in:
  pages that don't pass filter state never render a trigger.
- Settings-menu dropdown (z-60, fixed portal scale from the AGENTS z-index
  ladder: 0 content / 20 sticky / 50 modal / 60 fixed portal menus) closes on
  outside pointerdown or Escape.
- "Keep at least one column visible" guard in `toggleColumnVisibility`.

### lib/api.ts

- Refresh-on-401: 15-min access token expires while the SPA is open; on 401
  (non-auth endpoints) transparently POST /api/v1/auth/refresh (httpOnly
  `sf_refresh_token` cookie — JS never reads it) and retry once. Concurrent
  401s coalesce into a single /refresh via the shared `refreshPromise`.
  Auth endpoints are excluded (`REFRESH_SKIP_EXACT` +
  `/auth/company/` prefix) so a genuine auth failure is not mistaken for an
  expirable token. Retry body-safety: api.post/put/patch always stringify
  bodies; GET/DELETE carry none.
- `sessionExpiredHandler` setter-injection breaks the circular dependency
  lib/api <- store/authStore <- api/auth <- lib/api; the store registers the
  handler at module load, and the handler clears the session → RequireAuth
  redirects to /login.

### features/apps/types.ts

- Wire enums are the uppercase Java enum names; `AppValueFilter.value`
  semantics per operator follow backend `AppQueryValidator` (kept in code —
  contract).
- `AppRecord.values`: absent key = empty cell; JSON null = cleared value.
- Plan limits (`GET /apps/plan-limits`) come from the backend PlanDefinition
  registry — never hardcoded client-side; -1 = unlimited.
- `AppRequest` is a full PUT: the backend sets `icon` unconditionally, so
  `null` clears it (omit on create when empty).
  TRIVIA: old ViewType comment ("view CRUD/renderers land in a later part")
  was stale — renderers shipped with Epic 4.2 (K-42).

### features/apps/cellValue.ts

- `parseCellInput` triple-state contract: scalar to send / `null` clears /
  `undefined` = invalid, must not be submitted.
- `buildRecordPatch` partial-merge contract: only CHANGED keys present, `null`
  clears (required properties reject null server-side), untouched keys absent;
  empty object = nothing to send. For create, pass a record with an empty
  `values` map so every filled field becomes a change.
- "Emptying a filled cell clears it; an already-empty cell is a no-op."

### features/users/UserDetailPage.tsx

- One-page create/view/edit (`/users/new`, `/users/:userId`). Edit-mode save is
  diff-based and sequential (identity update → role set → group set), NOT
  atomic: unchanged assignments never trigger redundant session revocations; a
  mid-sequence failure keeps edit mode with drafts intact; re-save is
  idempotent (already-sent parts no longer dirty against the refetched user).
- Drafts are seeded ONCE in `startEdit`, never from a `user` effect — a
  background refetch (save invalidates `['users']`) cannot clobber edits.
- email/username are immutable in edit (backend updates only first/last name +
  enabled) — rendered as disabled inputs bound to the persisted user.
- Overflow menu invariants kept in code: hidden while editing (dirty form must
  not trigger parallel mutations); email re-send only pre-verification; unlock
  only during an active lock window (RISK-22 lazy expiry); self-delete omitted
  (backend 409 `self_delete_forbidden`).
- Head pattern (max one visible action + RowMenu overflow) and the save-footer
  placement rule live in frontend/AGENTS.md — no longer duplicated here.

### lib/useListPageState.ts (K-39/K-49)

- Contracts: new debounced search term / sort toggle / page-size change /
  filter change each reset the page to 0; page-size persists via storageKey.
- `listParams` shape `{page, size, sorts: [sort], q, qFields, filters}` with
  empty optional keys absent; scoped legacy params spread on top
  (`useNotes({ ...listParams, categoryId })`). `sorts` serialize to the same
  repeated `sort=field,dir` wire params as the raw string.
- DataTable wiring: `onPageSizeChange={setPageSize}`,
  `onSortChange={toggleSort}`, `onPageChange={setPage}`,
  `filters`/`onFiltersChange`, `toolbar={<SearchInput …/>}`.
- Client-paginated pages (single full response, e.g. permissions) use
  `useClientPagination`; they may take only the sort toggle from this hook —
  unused page/search state is inert when never wired.

### components/ui/ColumnFilterButton.tsx (K-49)

- Popover is a fixed-position body portal (z-60) like RowMenu/SelectInput
  menus — container overflow (short tables, `overflow-x-auto`) cannot clip it.
  Flips above the trigger near the viewport bottom (fallback height constant
  covers jsdom where measurement is unavailable).
- Outside scroll (including the table's overflow-x container) CLOSES the
  popover rather than repositioning the fixed portal — simpler and more
  predictable (same trade-off as RowMenu). Scrolls inside the panel or a
  SelectInput option menu keep the draft.
- Draft re-sync effect intentionally skips while open (deps suppressed) so an
  outside clause change (e.g. page reset) re-seeds only the closed state.

### components/pickers/ReferencePicker.tsx

- Monotonically-growing id→label map: search results merge in (never evicted),
  seeded from `selectedOptions` — single mode never flashes a raw id after a
  pick; multi mode keeps every chip labeled; unseen ids render raw.
- Label precedence: pick/search-fed map (fresh selection beats a stale
  caller-provided seed) → seed → raw id. TRIVIA: this comment block was
  duplicated verbatim in the file; deduplicated in this pass.
- `defaultOptions`: react-select only calls async loadOptions on non-empty
  input changes otherwise — menu-open must fetch the first page explicitly.

---

## frontend


### components/ui/DataTable.tsx (K-37/K-49-adjacent)

- `sortKey` MUST be in the backend feature's sort whitelist (SortGuard) or the
  request 400s — composite columns sort by a different field than `key`
  (users "name" column sorts by `email`).
- `filter` + `filters`/`onFiltersChange` are the K-49 column-filter opt-in:
  pages that don't pass filter state never render a trigger.
- Settings-menu dropdown (z-60, fixed portal scale from the AGENTS z-index
  ladder: 0 content / 20 sticky / 50 modal / 60 fixed portal menus) closes on
  outside pointerdown or Escape.
- "Keep at least one column visible" guard in `toggleColumnVisibility`.

### lib/api.ts

- Refresh-on-401: 15-min access token expires while the SPA is open; on 401
  (non-auth endpoints) transparently POST /api/v1/auth/refresh (httpOnly
  `sf_refresh_token` cookie — JS never reads it) and retry once. Concurrent
  401s coalesce into a single /refresh via the shared `refreshPromise`.
  Auth endpoints are excluded (`REFRESH_SKIP_EXACT` +
  `/auth/company/` prefix) so a genuine auth failure is not mistaken for an
  expirable token. Retry body-safety: api.post/put/patch always stringify
  bodies; GET/DELETE carry none.
- `sessionExpiredHandler` setter-injection breaks the circular dependency
  lib/api <- store/authStore <- api/auth <- lib/api; the store registers the
  handler at module load, and the handler clears the session → RequireAuth
  redirects to /login.

### features/apps/types.ts

- Wire enums are the uppercase Java enum names; `AppValueFilter.value`
  semantics per operator follow backend `AppQueryValidator` (kept in code —
  contract).
- `AppRecord.values`: absent key = empty cell; JSON null = cleared value.
- Plan limits (`GET /apps/plan-limits`) come from the backend PlanDefinition
  registry — never hardcoded client-side; -1 = unlimited.
- `AppRequest` is a full PUT: the backend sets `icon` unconditionally, so
  `null` clears it (omit on create when empty).
  TRIVIA: old ViewType comment ("view CRUD/renderers land in a later part")
  was stale — renderers shipped with Epic 4.2 (K-42).

### features/apps/cellValue.ts

- `parseCellInput` triple-state contract: scalar to send / `null` clears /
  `undefined` = invalid, must not be submitted.
- `buildRecordPatch` partial-merge contract: only CHANGED keys present, `null`
  clears (required properties reject null server-side), untouched keys absent;
  empty object = nothing to send. For create, pass a record with an empty
  `values` map so every filled field becomes a change.
- "Emptying a filled cell clears it; an already-empty cell is a no-op."

### features/users/UserDetailPage.tsx

- One-page create/view/edit (`/users/new`, `/users/:userId`). Edit-mode save is
  diff-based and sequential (identity update → role set → group set), NOT
  atomic: unchanged assignments never trigger redundant session revocations; a
  mid-sequence failure keeps edit mode with drafts intact; re-save is
  idempotent (already-sent parts no longer dirty against the refetched user).
- Drafts are seeded ONCE in `startEdit`, never from a `user` effect — a
  background refetch (save invalidates `['users']`) cannot clobber edits.
- email/username are immutable in edit (backend updates only first/last name +
  enabled) — rendered as disabled inputs bound to the persisted user.
- Overflow menu invariants kept in code: hidden while editing (dirty form must
  not trigger parallel mutations); email re-send only pre-verification; unlock
  only during an active lock window (RISK-22 lazy expiry); self-delete omitted
  (backend 409 `self_delete_forbidden`).
- Head pattern (max one visible action + RowMenu overflow) and the save-footer
  placement rule live in frontend/AGENTS.md — no longer duplicated here.

### lib/useListPageState.ts (K-39/K-49)

- Contracts: new debounced search term / sort toggle / page-size change /
  filter change each reset the page to 0; page-size persists via storageKey.
- `listParams` shape `{page, size, sorts: [sort], q, qFields, filters}` with
  empty optional keys absent; scoped legacy params spread on top
  (`useNotes({ ...listParams, categoryId })`). `sorts` serialize to the same
  repeated `sort=field,dir` wire params as the raw string.
- DataTable wiring: `onPageSizeChange={setPageSize}`,
  `onSortChange={toggleSort}`, `onPageChange={setPage}`,
  `filters`/`onFiltersChange`, `toolbar={<SearchInput …/>}`.
- Client-paginated pages (single full response, e.g. permissions) use
  `useClientPagination`; they may take only the sort toggle from this hook —
  unused page/search state is inert when never wired.

### components/ui/ColumnFilterButton.tsx (K-49)

- Popover is a fixed-position body portal (z-60) like RowMenu/SelectInput
  menus — container overflow (short tables, `overflow-x-auto`) cannot clip it.
  Flips above the trigger near the viewport bottom (fallback height constant
  covers jsdom where measurement is unavailable).
- Outside scroll (including the table's overflow-x container) CLOSES the
  popover rather than repositioning the fixed portal — simpler and more
  predictable (same trade-off as RowMenu). Scrolls inside the panel or a
  SelectInput option menu keep the draft.
- Draft re-sync effect intentionally skips while open (deps suppressed) so an
  outside clause change (e.g. page reset) re-seeds only the closed state.

### components/pickers/ReferencePicker.tsx

- Monotonically-growing id→label map: search results merge in (never evicted),
  seeded from `selectedOptions` — single mode never flashes a raw id after a
  pick; multi mode keeps every chip labeled; unseen ids render raw.
- Label precedence: pick/search-fed map (fresh selection beats a stale
  caller-provided seed) → seed → raw id. TRIVIA: this comment block was
  duplicated verbatim in the file; deduplicated in this pass.
- `defaultOptions`: react-select only calls async loadOptions on non-empty
  input changes otherwise — menu-open must fetch the first page explicitly.
