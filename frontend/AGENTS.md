# frontend/AGENTS.md

## Modül

React 19 + TypeScript + Vite SPA. Maven build (kök pom) backend jar'ına gömülür — `dist/` -> `backend/resources/static/`. Bağımsız npm build, backend'e Spring bağımlılığı yok. Kök AGENTS.md'deki genel kurallar geçerli.

Komutlar için bkz. [README](../README.md#build-komutlari). Frontend özet:

```bash
cd frontend
npm install --include=optional
npm run dev       # http://localhost:3000 (/api -> :8080 proxy)
npm run lint      # oxlint
npm run build     # tsc -b && vite build -> dist/
npm run preview   # build çıktısını lokal serve et
```

## Stack (gerçek versiyonlar `package.json`'dan)

Tüm bağımlılıklar **exact version** ile sabitlenir (`.npmrc`: `save-exact=true`).

- **dependencies:** `react` 19.2.7, `react-dom` 19.2.7
- **devDependencies:** `@types/node` 20.19.43, `@types/react` 19.2.17, `@types/react-dom` 19.2.3, `@vitejs/plugin-react` 6.0.3, `oxlint` 1.71.0, `typescript` 6.0.2, `vite` 8.1.1
- **Lint:** oxlint (`.oxlintrc.json` — plugin'ler: `react`/`typescript`/`oxc`; `react/rules-of-hooks`=error, `react/only-export-components`=[warn, `{allowConstantExport: true}`])
- Font: Outfit + Inter (Google Fonts, `index.html`)

## npm Konvansiyonları

- **`package-lock.json` kullanılmaz ve commit edilmez.** `.npmrc` içindeki `package-lock=false` lock üretimini kapatır. Maven/Docker da `npm install --include=optional --no-package-lock` kullanır; native optional paketler hedef platforma göre kurulur.
- `.npmrc`: `engine-strict=true`, `save-exact=true`, `package-lock=false`.
- `package.json` `engines`: `node >=20.18.0 <21.0.0`, `npm >=10.0.0 <11.0.0`. `.nvmrc`: `20.20.2` (kullanım: `nvm use`).

## Dev Server & Proxy

`vite.config.ts`: dev server `:3000`, `/api` ve `/actuator` istekleri `http://localhost:8080`'e proxy (`changeOrigin: true`). Backend ayrı (IDE) çalışırken CORS problemi yok.

## Yapı

- `src/App.tsx` — ana dashboard (~380 satır). Mock veri `TENANT_DATA` inline (3 tenant: acme/stark/wayne).
- `src/main.tsx` — entry (`createRoot` + `StrictMode`).
- `src/index.css` — global CSS değişkenleri (light/dark `color-scheme`, accent `#aa3bff`).
- `src/App.css` — dashboard stilleri. **Dashboard her zaman dark** (`--bg-primary: #0a0a0f` sabit, `#root` `!important` ile index.css'i ezer). `index.css`'te light/dark değişkenleri tanımlı olsa da pratikte görünmez.
- `src/assets/` — `react.svg`, `vite.svg`, `hero.png`.
- `index.html`, `tsconfig.json` (project references: `tsconfig.app.json` + `tsconfig.node.json`).

## Backend Health Kontrolü

`App.tsx` `fetch('/actuator/health')` ile backend up/down kontrol eder (mount'ta `useEffect`). Rozet metinleri (`App.tsx`):
- Loading: `Connecting...`
- UP: `Backend UP`
- DOWN: `Backend DOWN`
- Ayrıca sabit `Demo Data` rozeti (mock veri için).

## Gotcha'lar

- **Mevcut frontend mock veriyle çalışır** — gerçek API entegrasyonu YOK. Tüm liste/tablo verileri `TENANT_DATA` mock'undan gelir. Faz 4'te yeniden yazılacak.
- **Planlanan ama henüz YOK:** TanStack Query, Zustand, Tailwind CSS, react-router-dom, Vitest, React Testing Library, Playwright. Mevcut stil custom CSS.
