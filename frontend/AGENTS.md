# frontend/AGENTS.md

## Module

React 19 + TypeScript + Vite SPA. The Maven build (root pom) embeds it in the backend jar — `dist/` -> `backend/resources/static/`. Independent npm build; no Spring dependency on the backend. General rules from the root AGENTS.md apply.

For commands see [README](../README.md#build-komutları). Frontend summary:

```bash
cd frontend
npm install --include=optional
npm run dev       # http://localhost:3000 (/api -> :8080 proxy)
npm run lint      # oxlint
npm run build     # tsc -b && vite build -> dist/
npm run preview   # serve the build output locally
```

## Stack (real versions from `package.json`)

Every dependency is pinned to an **exact version** (`.npmrc`: `save-exact=true`).

- **dependencies:** `react` 19.2.7, `react-dom` 19.2.7
- **devDependencies:** `@types/node` 20.19.43, `@types/react` 19.2.17, `@types/react-dom` 19.2.3, `@vitejs/plugin-react` 6.0.3, `oxlint` 1.71.0, `typescript` 6.0.2, `vite` 8.1.1
- **Lint:** oxlint (`.oxlintrc.json` — plugins: `react`/`typescript`/`oxc`; `react/rules-of-hooks`=error, `react/only-export-components`=[warn, `{allowConstantExport: true}`])
- Font: Outfit + Inter (Google Fonts, `index.html`)

## npm conventions

- **`package-lock.json` is NOT used and NOT committed.** `.npmrc` sets `package-lock=false` to disable lock generation. Maven/Docker also use `npm install --include=optional --no-package-lock`; native optional packages are selected per target platform.
- `.npmrc`: `engine-strict=true`, `save-exact=true`, `package-lock=false`.
- `package.json` `engines`: `node >=20.18.0 <21.0.0`, `npm >=10.0.0 <11.0.0`. `.nvmrc`: `20.20.2` (use `nvm use`).

## Dev server & proxy

`vite.config.ts`: dev server on `:3000`, `/api` and `/actuator` requests are proxied to `http://localhost:8080` (`changeOrigin: true`). No CORS problem while the backend runs separately (IDE).

## Layout

- `src/App.tsx` — main dashboard (~380 lines). Mock data `TENANT_DATA` inline (3 tenants: acme/stark/wayne).
- `src/main.tsx` — entry (`createRoot` + `StrictMode`).
- `src/index.css` — global CSS variables (light/dark `color-scheme`, accent `#aa3bff`).
- `src/App.css` — dashboard styles. **The dashboard is always dark** (`--bg-primary: #0a0a0f` is fixed, `#root` overrides index.css with `!important`). The light/dark variables defined in `index.css` are effectively invisible.
- `src/assets/` — `react.svg`, `vite.svg`, `hero.png`.
- `index.html`, `tsconfig.json` (project references: `tsconfig.app.json` + `tsconfig.node.json`).

## Backend health check

`App.tsx` calls `fetch('/actuator/health')` to detect backend up/down (in a mount `useEffect`). Badge texts (`App.tsx`):
- Loading: `Connecting...`
- UP: `Backend UP`
- DOWN: `Backend DOWN`
- Plus a static `Demo Data` badge (for mock data).

## Gotchas

- **The current frontend runs on mock data** — there is NO real API integration. All list/table data comes from the `TENANT_DATA` mock. It is rewritten in Phase 4.
- **Planned but NOT yet present:** TanStack Query, Zustand, Tailwind CSS, react-router-dom, Vitest, React Testing Library, Playwright. Current styling is custom CSS.
