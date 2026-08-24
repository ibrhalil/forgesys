# frontend/AGENTS.md

## Module

React 19 + TypeScript + Vite SPA. The Maven build (root pom) embeds it in the backend jar — `dist/` -> `backend/resources/static/`. Independent npm build; no Spring dependency on the backend. General rules from the root AGENTS.md apply.

For commands see [README](../README.md#build-komutları). Frontend summary:

```bash
cd frontend
npm install --include=optional
npm run dev       # http://localhost:3000 (/api -> :8080 proxy)
npm run lint      # oxlint
npm test          # vitest run (jsdom + React Testing Library)
npm run build     # tsc -b && vite build -> dist/
npm run preview   # serve the build output locally
```

## Stack (real versions from `package.json`)

Every dependency is pinned to an **exact version** (`.npmrc`: `save-exact=true`).

- **dependencies:** `@tanstack/react-query` 5.102.1, `react`/`react-dom` 19.2.8, `react-icons` 5.7.0, `react-markdown` 10.1.0 + `remark-gfm` 4.0.1 (K-44 notes preview — raw HTML deliberately NOT rendered, `rehype-raw` absent), `react-router-dom` 7.18.2, `react-select` 5.10.2, `react-toastify` 11.1.0, `zustand` 5.0.15
- **devDependencies:** `@tailwindcss/vite` 4.3.3 + `tailwindcss` 4.3.3, `@types/*`, `@vitejs/plugin-react` 6.1.0, `oxlint` 1.79.0, `typescript` 6.0.3, `vite` 8.2.2, `vitest` 4.1.11 + `jsdom` 30.0.1 + `@testing-library/react` 16.3.2 (+dom 19.x) + `@testing-library/jest-dom` 7.0.1 + `@testing-library/user-event` 14.6.6
- **Lint:** oxlint (`.oxlintrc.json` — plugins: `react`/`typescript`/`oxc`; `react/rules-of-hooks`=error, `react/only-export-components`=[warn, `{allowConstantExport: true}`])
- **Styling:** Tailwind CSS v4 via `@theme` tokens in `src/index.css` (no `tailwind.config`). Font: Outfit + Inter (Google Fonts, `index.html`).

## npm conventions

- **`package-lock.json` is NOT used and NOT committed.** `.npmrc` sets `package-lock=false`. Maven/Docker use `npm install --include=optional --no-package-lock`.
- `.npmrc`: `engine-strict=true`, `save-exact=true`, `package-lock=false`.
- `engines`: node `>=24.0.0 <25.0.0`, npm `>=11 <12`. `.nvmrc`: `24.19.0`.

## Dev server & proxy

`vite.config.ts`: dev server on `:3000`; `/api` and `/actuator` proxied to `http://localhost:8080` (`changeOrigin: true`). No CORS issues while the backend runs separately (IDE).

## Directory layout (folder-by-feature)

```
src/
  main.tsx                 # entry (StrictMode + ErrorBoundary + Toaster)
  app/App.tsx              # providers (QueryClient) + router; shell children are
                           # generated from app/Routes.ts (public routes + layout explicit)
  app/Routes.ts            # SHELL_ROUTES config list: path/Component/authority per route —
                           # App maps it to <Route> + RequirePermission (authorities mirror
                           # app/Navigation.ts). Pages are React.lazy chunks; AppShell's
                           # <Suspense> around <Outlet/> covers chunk loading
  app/Navigation.ts        # data-driven sidebar config: NAV_ITEMS + NAV_GROUPS
                           # (labelKey/to/icon/authority per item; authority-less groups
                           # hide entirely)
  lib/permissions.ts       # PERMISSIONS constants — the built-in catalog mirrored from
                           # the backend PermissionCatalog (single frontend source)
  components/              # cross-feature/shared components
    AppShell.tsx           #   viewport-locked shell (lg:h-screen): fixed sidebar + fixed
                           #   breadcrumb topbar; page scrolls inside its own container.
                           #   Sidebar renders Navigation.ts (authority-filtered, empty
                           #   groups hidden), react-icons/lu icons, user chip, LanguageToggle
    RequireAuth.tsx        #   auth redirect guard
    RequirePermission.tsx  #   permission route guard — inline 403 state (no redirect)
    Page.tsx               #   page scaffold: head (title/description/actions) over body —
                           #   every routed screen renders through this; breadcrumb portals
                           #   into the AppShell topbar (inline fallback without a shell)
    Breadcrumb.tsx         #   `Identity & Access > Groups > Developer` path line (last
                           #   segment = current page; `to` segments are links)
    BreadcrumbTargetContext.ts # portal target exposed by AppShell, consumed by Page
    LanguageToggle.tsx     #   TR/EN segmented switch
    ErrorBoundary.tsx, Toaster.tsx
    detail/                #   DetailPanel/DetailField/PermissionBadges + AssignSection (IAM detail pages)
    pickers/               #   reference-data selects: UserPicker/RolePicker/GroupPicker/ProjectPicker/
                           #   AppPicker (async `q` typeahead over list endpoints, single or isMulti,
                           #   id→label bookkeeping with fallback ids) + useDebouncedLoadOptions
                           #   (debounce + stale-response guard). Reference-data selects use these —
                           #   never capped one-page list fetches.
    ui/                    #   design system: Badge, Button, CheckboxList, ConfirmDialog,
                           #   DataTable (sortable headers + `toolbar` filter slot + SearchInput),
                           #   EmptyState, Field, Modal, RowMenu (row/page-head overflow menu —
                           #   callers filter items by permission; empty items = no trigger),
                           #   SelectInput, TextArea, Spinner (single animate-spin source),
                           #   Toggle (boolean-setting switch)
  features/<name>/         # ONE folder per domain: pages + api.ts + hooks.ts + types.ts (+ components/)
    auth/                  #   Login/Register/VerifyTenant pages, authApi, registrationApi, types
                           #   (K-21 tenant signup lives here)
    users/                 #   Users/UserDetail/Profile pages + 5 modal components (assign
                           #   modals fetch the user detail themselves — list rows are the
                           #   flat UserDirectoryView projection: counts, no role/group arrays)
    roles/  groups/  permissions/
    projects/              #   Typed project containers (K-45): ProjectsPage (create modal's type
                           #   options derive from the ACTIVE-module catalog — useProjectTypes), the
                           #   three-way ProjectDetailPage switch (TaskBoard / ProjectNotesPanel /
                           #   ProjectAppsPanel — panels live in their feature folders, cross-feature
                           #   hook/panel imports), TaskBoard (components/)
    modules/               #   Module catalog + activation page (K-16, iam:module:read)
    apps/                  #   App Builder UI (Epic 4.2 / K-42, K-45 project-scoped): app (project
                           #   column + AppFormModal projectId anchor)/property/view/record CRUD,
                           #   RecordFormModal (create+edit), TABLE/BOARD/CALENDAR/LIST/GALLERY
                           #   renderers, row-based filter/sort DSL editor (client-applied),
                           #   User/Relation pickers + id→label resolvers, plan usage indicators
                           #   (GET /apps/plan-limits — numbers from the backend registry) +
                           #   components/ProjectAppsPanel (the APPS container body)
     audit/                 #   AuditLogs + LoginHistory (iam:audit:read)
     sessions/              #   self/admin/all sessions pages + SessionList component (K-28)
     notes/                 #   Notes module (K-44, K-45 project-scoped): NotesPage (DataTable +
                            #   category filter + pinned toggle + project column), NoteEditorPage
                            #   (target-project selector for new notes — ?projectId= or the catalog
                            #   default; categories follow the chosen container; markdown preview via
                            #   react-markdown, raw HTML never rendered) and components/ProjectNotesPanel
                            #   (the NOTES container body inside ProjectDetailPage).
  lib/                     # api (fetch + 401 refresh), i18n (t/useT + messages), notify, format, select, cn,
                           # useListPageState (list-page scaffold: page/sort/search + debounce + page-reset),
                           # useClientPagination, useDebouncedValue
  store/                   # zustand: authStore (session + authorities), tenantStore (X-Tenant-ID),
                           # localeStore (sf_locale, TR/EN)
  test/                    # Vitest suite (K-39): setup.ts + feature/primitive tests (api refresh, LoginPage,
                            # DataTable, Modal, useListPageState, apps/notes/projects feature pages — see src/test/)
  types/index.ts           # shared-only types: RBAC summaries, ApiErrorResponse, pagination
```

### Feature conventions

- `features/X/api.ts` — plain fetch wrappers per endpoint group (uses `api` from `lib/api`).
- `features/X/hooks.ts` — TanStack Query hooks; **query keys are the collection name** (`['users', params]`, `['roles', id]`, `['users', id, 'effective-permissions']`); mutations invalidate their collection prefix.
- **Server-side list search/sort:** pages get the whole scaffold from `lib/useListPageState` (`{page, setPage, pageSize, setPageSize, sort, toggleSort, search, setSearch, q}` — debounced search + the page-reset contracts) and pass `sorts: [sort]` and `q` inside `PageParams`. `Column.sortKey` marks a DataTable column sortable (may differ from `key` — the users "name" column sorts by `email`); the value MUST be in the backend feature's sort whitelist or the request 400s. `PageResponse` is the API-owned wire shape (`data[] + meta`), normalized by `normalizePage` (legacy Spring Data layout tolerated).
- **Rows-per-page:** choices live in `lib/pagination.ts` (`PAGE_SIZE_OPTIONS`, backend cap 1000); pages hold `pageSize` state and reset to page 0 on change. DataTable renders minimal segment buttons up to 6 options, then a ghost native `<select>` — extending the list never crowds the footer. Pages whose backend returns the full list in one response (e.g. permissions) paginate locally via `lib/useClientPagination` and wire the footer identically.
- `features/X/types.ts` — request/response types for that domain. Shared summaries (`RoleSummary` etc.) stay in `src/types/index.ts`.
- Cross-feature imports are allowed (e.g. groups pages use `useRoles`); keep them at hook/type level, not page level.
- `authStore`/`tenantStore` are cross-feature by design and live in `src/store/`.
- **List tables stay narrow:** association columns (a user's roles, a role's permissions, a group's members) render a count chip (or an `ALL` badge for the `all_permissions` flag), never the full list — the detail page shows it. New tables follow this pattern; use the DataTable `toolbar` slot for filters.

## Auth & tenant context (critical)

- Access/refresh tokens are **httpOnly cookies** (`sf_access_token`, `sf_refresh_token`); JS never reads them.
- `lib/api.ts` `apiFetch`: on 401 (non-auth endpoints) transparently calls `/api/v1/auth/refresh` once (concurrent 401s coalesce via `refreshPromise`) and retries. If refresh fails, `sessionExpiredHandler` (wired by `authStore`) clears the session → `RequireAuth` redirects to `/login`.
- `tenantStore` resolves tenant from localStorage (`sf_tenant_id`) or subdomain; every request carries `X-Tenant-ID` (dev-profile `TenantFilter`).
- `authStore.hasAuthority('iam:user:write')` gates UI; the backend enforces the real security.

## Error/notification flow

- Global `QueryClient` `mutations.onError` → `notifyApiError` (toast) — skips field-level validation (`fields[]`, rendered inline by forms) and 401 (redirect).
- `extractFieldErrors(err)` → `Record<field, message>` for inline rendering.

## i18n

- Homegrown, zero-dependency: `src/lib/i18n/` — `messages.ts` (TR + EN dictionaries, flat keys namespaced `common.*`/`nav.*`/`table.*`/`<feature>.*`), `useT()` reactive hook for components, imperative `t()` for event handlers/toasts/stores. `{param}` interpolation supported.
- `store/localeStore.ts` — locale persisted in localStorage (`sf_locale`), default `tr`; TR/EN segmented switch in the AppShell footer (`components/LanguageToggle.tsx`).
- **Rules:** never hardcode user-visible strings — add the key to BOTH `tr` and `en` dictionaries (`tr` is the source of truth for the key set; `MessageKey` type keeps keys compile-checked). Backend `ErrorCode` → key maps (e.g. VerifyTenantPage `ERROR_KEYS`) resolve at render time.

## Gotchas

- **z-index scale:** `0` normal content · `20` sticky elements · `50` modal overlay (`Modal`) · `60` fixed portal menus (`RowMenu`, SelectInput menu). New surfaces pick from this scale — don't invent intermediate values.
- **Size & spacing scale (keep everything on it):** page body `p-6 lg:p-10` (matches topbar `px`); sections `gap-6`; cards/panels `p-5` (DetailPanel — never hand-rolled card sections); action footers `mt-4 flex justify-end gap-3` with default-size (md) buttons; controls (inputs, md buttons) sit on a ~36px height rhythm; empty/loading states `py-16`. Don't invent new paddings for new surfaces — reuse these.
- **Toggle vs Checkbox:** boolean SETTINGS (account enabled, group active, all-permissions) render as `Toggle` (`role="switch"`). Multi-select LISTS render as pickers/checkboxes by size: large or reference data (roles, groups, users, projects, apps — anything server-side `q`-searchable) uses the async `components/pickers/*` in `isMulti` mode; `CheckboxList` stays for small, bounded lists only (e.g. permission catalogs). A switch communicates a single state, not selection.
- **Page head actions:** at most TWO visible controls in the `Page` head — the primary action (list pages' create button) or the most frequent action (detail pages' Edit, `sm` ghost + pencil icon) plus a `RowMenu` overflow (`icon={LuEllipsisVertical}`) for everything else. Destructive actions live ONLY inside the overflow (danger tone), never as top-level buttons. Pass permission-filtered items — an empty array renders no trigger.
- **Save/Cancel placement:** commit actions always sit bottom-right of the editing surface — modal footers (`Modal` renders them `justify-end`) or a `mt-4 flex justify-end gap-3` footer row inside the card/panel, with default-size (md) buttons. Never place Save in a `DetailPanel` header (the `action` prop was removed), left-aligned, or in `sm` size.
- **Scroll architecture (sticky elements):** on desktop the shell is viewport-locked (`lg:h-screen` + `lg:overflow-hidden`) — the sidebar and the breadcrumb topbar are fixed; ONLY the page body scrolls, inside AppShell's `flex-1 overflow-y-auto` container. Sticky elements inside pages (e.g. future sticky DataTable headers) must use plain `top-0` relative to that scroll container — never offset for the topbar (it is outside the scroller) and never `position: fixed`. Below lg the shell keeps natural page scroll; the mobile nav is an off-canvas drawer (z-50) — sticky-in-page rules only apply inside the desktop scroll container.
- **Zustand selectors:** subscribe with primitive/action selectors (`useAuthStore((s) => s.isLoading)`), never destructure the whole store — action references are stable, whole-store subscriptions re-render on every write. `AppShell` intentionally subscribes to the `user` object so the authority-filtered nav re-renders on session changes.
- **Loading spinner bootstrap:** `authStore.isLoading` is bootstrap-only (`/me` check); never reuse it for login submission (router unmounts).
- **Query key discipline:** list queries key on the params object (`['users', params]`); detail on id. Effective-permissions keys are `['users', id, 'effective-permissions']`.
- **`SelectInput`** is the single select component (react-select). Behavior is prop-driven: single (default), `isMulti`, `isClearable`, `creatable`, async `loadOptions`, and `size="sm"` for compact inline controls (e.g. TaskCard status mover). Menu renders in a portal (escapes Modal overflow). The old native `SelectField` was removed.
- **Icons:** `react-icons` (Lucide set, subpath import `react-icons/lu` — Vite tree-shakes). Never add inline SVGs; pick a Lucide icon.
- **Light corporate theme tokens:** `src/index.css` `@theme` — pale-sky page bg (`--color-bg: #e0f2fe`), white surfaces (`--color-surface/sidebar`), raspberry accent (`--color-accent: #c2185b`); utilities like `bg-surface`/`text-muted`/`border-glass`. App is light-only (dark theme removed). Never use raw `text-white`/`bg-white/5` outside the gradient logo tiles — use tokens so a future theme stays possible.
- **Tests (K-39):** Vitest + React Testing Library, `npm test` (CI runs it too). Suite lives in `src/test/` (`setup.ts` + `*.test.ts(x)`); config in `vitest.config.ts` (jsdom, `globals: false` — tests import `describe/it/expect/vi` from `'vitest'` explicitly; `setup.ts` registers RTL cleanup + jest-dom matchers). Mocks: `vi.stubGlobal('fetch', ...)` for `lib/api` (no MSW), `useStore.setState({...})` for zustand stores, `useLocaleStore.setState({ locale: 'en' })` for stable query strings. **A new frontend feature does not merge without tests** — at minimum a hook/logic test plus a render test for new UI primitives.

## Planned: DataTable enhancements

### 1. Bulk Operations *(planned — not started)*

**Goal:** Checkbox-based multi-row selection in `DataTable`, floating bulk-action bar, extensible `bulkActions` prop. Requires user approval before starting.

**Scope:**
- `DataTable` gains an optional `bulkActions?: BulkAction<T>[]` prop. When provided, a checkbox column is prepended automatically.
- Header checkbox: checked if all visible rows selected; indeterminate if some are.
- Shift+Click for range selection.
- When ≥1 rows are selected, a floating action bar appears at the bottom of the table (above the pagination row) with: selection count, provided bulk action buttons, and a "Clear selection" link.
- Built-in `bulkDelete` convenience action: shows a `ConfirmDialog` then calls a provided `onBulkDelete(ids: string[])` callback; clears selection on success.
- Custom actions receive `(selectedItems: T[]) => void` — the caller decides the backend call.
- Selection is **not persisted** (ephemeral per-page; cleared on page/filter change).
- Wiring pages: `UsersPage` (bulk delete), others as needed.

**Files to touch:** `DataTable.tsx`, affected list pages, i18n `messages.ts`.

---

### 2. Table View Modes — **IMPLEMENTED**

Shipped: `viewModes?: TableViewMode[]` (table/card/list) + the toolbar view-switcher (`table.viewMode`), `cardRender?: (row: T) => ReactNode` / `listRender?: (row: T) => ReactNode` custom renderers (auto-generated structured cards when omitted), column visibility + density (`compact/normal/relaxed`) and the persisted `viewMode` — all stored via `tablePreferences` under the table's `storageKey` in localStorage. Per-column hiding keeps `hideable: false` primary columns visible. No further work planned here; new render modes follow the existing prop pattern.
