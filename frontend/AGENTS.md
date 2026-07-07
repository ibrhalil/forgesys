# frontend/AGENTS.md

## Modül

React 19 + TypeScript + Vite SPA. Maven build (kök pom) backend jar'ına gömülür — `dist/` → `backend/resources/static/`. Bağımsız npm build, backend'e Spring bağımlılığı yok. Kök AGENTS.md'deki genel kurallar geçerli.

## Komutlar

```bash
cd frontend
npm install
npm run dev       # http://localhost:3000 (/api -> :8080 proxy)
npm run lint      # oxlint
npm run build     # tsc -b && vite build -> dist/
npm run preview   # build çıktısını lokal serve et
```

## Stack (gerçek versiyonlar `package.json`'dan)

- **React** `^19.2.7`, **TypeScript** `~6.0.2`, **Vite** `^8.1.1`, `@vitejs/plugin-react` `^6.0.3`
- **Lint:** oxlint `^1.71.0` (`.oxlintrc.json` — react/typescript plugin'leri; `react/rules-of-hooks`=error, `react/only-export-components`=warn)
- Font: Outfit + Inter (Google Fonts)

## Dev Server & Proxy

`vite.config.ts`: dev server `:3000`, `/api` ve `/actuator` istekleri `http://localhost:8080`'e proxy. Backend ayrı (IDE) çalışırken CORS problemi yok.

## Yapı

- `src/App.tsx` — ana dashboard (mock veri `TENANT_DATA`, ~356 satır).
- `src/main.tsx` — entry (`createRoot` + `StrictMode`).
- `src/index.css` / `App.css` — custom CSS (light/dark, purple accent `#aa3bff`).
- `index.html`, `tsconfig.json` (project references: `tsconfig.app.json` + `tsconfig.node.json`).

## Gotcha'lar

- **Mevcut frontend mock veriyle çalışır** — gerçek API entegrasyonu YOK. `GET /actuator/health` ile backend'in up/down durumunu kontrol eder; up ise "Spring Boot Connected", yoksa "Offline Mode" rozeti gösterir. Tüm liste/tablo verileri `TENANT_DATA` mock'undan gelir. Faz 4'te yeniden yazılacak.
- **Planlanan ama henüz YOK:** TanStack Query v5, Zustand v5, Tailwind CSS, react-router-dom, Vitest, React Testing Library, Playwright. Mevcut stil custom CSS.
- **`package-lock.json` commit edilmeli** (`npm ci` Docker build için).
