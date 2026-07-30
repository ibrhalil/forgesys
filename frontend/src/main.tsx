import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './app/App.tsx'
import { ErrorBoundary } from './components/ErrorBoundary'
import { Toaster } from './components/Toaster'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
    {/* Toaster sits outside the ErrorBoundary so it stays mounted even if the app
        tree crashes — a crash fallback may still want to surface a toast. */}
    <Toaster />
  </StrictMode>,
)
