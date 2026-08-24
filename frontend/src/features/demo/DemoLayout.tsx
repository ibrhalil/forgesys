import { Suspense } from 'react';
import { NavLink, Outlet, Navigate, useLocation } from 'react-router-dom';
import {
  LuFlaskConical,
  LuTable2,
  LuTag,
  LuMousePointerClick,
  LuLayers,
  LuTextCursorInput,
  LuMenu,
} from 'react-icons/lu';
import { cn } from '../../lib/cn';
import { Spinner } from '../../components/ui/Spinner';

interface NavItem {
  label: string;
  to: string;
  icon: React.ElementType;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'DataTable', to: '/demo/datatable', icon: LuTable2 },
  { label: 'Badge', to: '/demo/badge', icon: LuTag },
  { label: 'Button & Spinner', to: '/demo/button', icon: LuMousePointerClick },
  { label: 'Form Controls', to: '/demo/form', icon: LuTextCursorInput },
  { label: 'Modal & Dialogs', to: '/demo/modal', icon: LuLayers },
  { label: 'RowMenu & Empty', to: '/demo/rowmenu', icon: LuMenu },
];


/**
 * Standalone layout for the /demo route tree. Lives entirely outside AppShell:
 * no auth, no sidebar, no breadcrumb topbar. DEV-only guard is enforced in App.tsx.
 */
export function DemoLayout() {
  const location = useLocation();

  // Redirect bare /demo to first page
  if (location.pathname === '/demo' || location.pathname === '/demo/') {
    return <Navigate to="/demo/datatable" replace />;
  }

  return (
    <div className="flex min-h-screen">
      {/* Sidebar */}
      <aside className="w-56 shrink-0 border-r border-glass bg-sidebar flex flex-col">
        {/* Header */}
        <div className="flex items-center gap-2.5 border-b border-glass px-4 py-4">
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-accent/15">
            <LuFlaskConical className="h-4 w-4 text-accent" />
          </div>
          <div>
            <p className="text-sm font-bold text-main leading-tight">UI Components</p>
            <span className="inline-flex items-center rounded-full bg-warning/15 px-1.5 py-px text-[10px] font-semibold uppercase tracking-wide text-warning border border-warning/30">
              dev only
            </span>
          </div>
        </div>

        {/* Nav */}
        <nav className="flex-1 overflow-y-auto p-2" aria-label="Component categories">
          <p className="mb-1 px-2 text-[10px] font-semibold uppercase tracking-widest text-muted">
            Components
          </p>
          <ul className="space-y-0.5">
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm transition-colors',
                      isActive
                        ? 'bg-accent/10 font-semibold text-accent'
                        : 'text-muted hover:bg-main/5 hover:text-main',
                    )
                  }
                >
                  <item.icon className="h-4 w-4 shrink-0" aria-hidden />
                  <span className="truncate">{item.label}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        {/* Footer */}
        <div className="border-t border-glass px-4 py-3">
          <p className="text-xs text-muted/60">ForgeSys · Dev Tools</p>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-5xl p-6 lg:p-10">
          <Suspense
            fallback={
              <div className="flex justify-center py-16">
                <Spinner className="border-muted/40 border-t-accent" />
              </div>
            }
          >
            <Outlet />
          </Suspense>
        </div>
      </main>
    </div>
  );
}
