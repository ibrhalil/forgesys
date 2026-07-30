import { Suspense, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { LuChevronDown, LuLogOut } from 'react-icons/lu';
import { useAuthStore } from '../store/authStore';
import { useMe } from '../features/users/hooks';
import { NAV_GROUPS, NAV_ITEMS, type NavItem } from '../app/Navigation';
import { BreadcrumbTargetContext } from './BreadcrumbTargetContext';
import { LanguageToggle } from './LanguageToggle';
import { ConfirmDialog } from './ui/ConfirmDialog';
import { Spinner } from './ui/Spinner';
import { useT } from '../lib/i18n';
import { cn } from '../lib/cn';

const navBase =
  'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors';
const navInactive = 'text-muted hover:bg-accent/5 hover:text-accent';
const navActive = 'bg-accent/10 text-accent';

function navClass({ isActive }: { isActive: boolean }) {
  return cn(navBase, isActive ? navActive : navInactive);
}

/** One sidebar entry. Visibility is decided by the caller (authority filter). */
function NavEntry({ item }: { item: NavItem }) {
  const { t } = useT();
  const Icon = item.icon;
  return (
    <NavLink to={item.to} end={item.to === '/'} className={navClass}>
      <Icon className="h-[18px] w-[18px] shrink-0" />
      {t(item.labelKey)}
    </NavLink>
  );
}

/**
 * Collapsible nav group. Header toggles open/closed (chevron rotates); the state is
 * persisted in localStorage per group id so it survives reloads. Items render on a
 * subtle tree guide line.
 */
function NavSection({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  const [open, setOpen] = useState(() => localStorage.getItem(`sf_nav_${id}`) !== 'closed');

  const toggle = () => {
    setOpen((prev) => {
      localStorage.setItem(`sf_nav_${id}`, prev ? 'closed' : 'open');
      return !prev;
    });
  };

  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        onClick={toggle}
        aria-expanded={open}
        className="flex w-full items-center justify-between rounded-md px-3 py-2 text-[13px] font-semibold text-main/80 transition-colors hover:bg-accent/5 hover:text-accent"
      >
        <span>{title}</span>
        <LuChevronDown className={cn('h-4 w-4 text-muted transition-transform', open ? 'rotate-180' : 'rotate-0')} />
      </button>

      {open && (
        <div className="ml-4 flex flex-col gap-1 border-l border-glass/70 pl-2">
          {children}
        </div>
      )}
    </div>
  );
}

export function AppShell() {
  // Primitive/action selectors — but `user` (object) stays subscribed on purpose:
  // the nav authority filter must re-run when the session/authorities change.
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const hasAuthority = useAuthStore((s) => s.hasAuthority);
  const navigate = useNavigate();
  const { t } = useT();
  // Full profile for the user chip (shares the ['users','me'] query cache with the
  // Profile page — no extra request). Falls back through username -> full name -> email.
  const { data: me } = useMe();

  const [confirmingLogout, setConfirmingLogout] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  // The topbar element Pages portal their breadcrumb into (set once on mount).
  const [breadcrumbTarget, setBreadcrumbTarget] = useState<HTMLDivElement | null>(null);

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
      navigate('/login');
    } finally {
      setLoggingOut(false);
      setConfirmingLogout(false);
    }
  };

  const fullName = [me?.firstName, me?.lastName].filter(Boolean).join(' ');
  const displayName = me?.username || fullName || user?.email || '—';
  const initial = displayName.charAt(0).toUpperCase();

  // Authority-driven visibility: a user without the required authority sees neither
  // the item nor the group it lives in (empty groups hide entirely).
  const visibleItems = NAV_ITEMS.filter((i) => !i.authority || hasAuthority(i.authority));

  return (
    <BreadcrumbTargetContext.Provider value={breadcrumbTarget}>
      {/* Viewport-locked shell on desktop (sidebar + topbar stay fixed; only the
          page body scrolls inside its own container). Mobile keeps natural page
          scroll — the grid grows and the page scrolls as a whole. */}
      <div className="grid min-h-screen grid-cols-1 lg:h-screen lg:grid-cols-[260px_1fr] lg:overflow-hidden">
        <aside className="relative flex flex-col border-b border-glass bg-sidebar p-5 shadow-xl shadow-black/5 lg:border-b-0 lg:border-r">
          {/* Thin raspberry spine — anchors the sidebar visually. */}
          <span aria-hidden className="absolute inset-y-0 left-0 hidden w-1 bg-gradient-to-b from-accent to-accent-blue lg:block" />

          <div className="mb-6 flex items-center gap-3 px-1">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-accent to-accent-blue text-lg font-bold text-white shadow-lg shadow-accent/30">
              SF
            </div>
            <div className="flex min-w-0 flex-col">
              <h2 className="m-0 text-lg font-semibold leading-tight tracking-tight text-main">ForgeSys</h2>
              <span className="text-xs text-muted">{t('nav.workspace')}</span>
            </div>
          </div>

          <nav className="flex flex-1 flex-col gap-1 overflow-y-auto">
            {visibleItems.map((item) => (
              <NavEntry key={item.to} item={item} />
            ))}

            {NAV_GROUPS.map((group) => {
              const items = group.items.filter((i) => !i.authority || hasAuthority(i.authority));
              if (items.length === 0) return null;
              return (
                <NavSection key={group.id} id={group.id} title={t(group.labelKey)}>
                  {items.map((item) => (
                    <NavEntry key={item.to} item={item} />
                  ))}
                </NavSection>
              );
            })}
          </nav>

          <div className="mt-3 flex flex-col gap-3 border-t border-glass pt-4">
            <div className="flex items-center justify-between px-1">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-muted/70">{t('nav.language')}</span>
              <LanguageToggle />
            </div>

            <div className="flex items-center gap-2">
              <NavLink
                to="/profile"
                className="flex min-w-0 flex-1 items-center gap-2.5 rounded-lg p-1.5 transition-colors hover:bg-accent/5"
                title={user?.email ?? ''}
              >
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-accent/10 text-sm font-semibold text-accent">
                  {initial}
                </span>
                <span className="truncate text-left text-sm text-main">{displayName}</span>
              </NavLink>
              <button
                onClick={() => setConfirmingLogout(true)}
                className="shrink-0 rounded-lg border border-glass p-2 text-muted transition-colors hover:border-accent/40 hover:text-accent"
                aria-label={t('nav.logout')}
                title={t('nav.logout')}
              >
                <LuLogOut size={16} />
              </button>
            </div>
          </div>
        </aside>

        <main className="flex min-h-0 flex-col">
          {/* Fixed breadcrumb topbar (Page portals its breadcrumb here). It lives
              OUTSIDE the scroll container so it never scrolls away — and future
              sticky elements like table headers can use plain `top-0` inside the
              scroll container without offset coordination. */}
          <div
            ref={setBreadcrumbTarget}
            className="flex h-11 shrink-0 items-center overflow-x-auto border-b border-glass px-6 lg:px-10"
          />
          <div className="flex-1 overflow-y-auto p-6 lg:p-10">
            {/* Route-level code splitting: pages are lazy chunks (app/Routes.ts); the
                fallback keeps the shell mounted while a chunk loads. */}
            <Suspense
              fallback={(
                <div className="flex items-center justify-center py-16">
                  <Spinner size="lg" className="border-glass border-t-accent" />
                </div>
              )}
            >
              <Outlet />
            </Suspense>
          </div>
        </main>

        <ConfirmDialog
          open={confirmingLogout}
          title={t('nav.logoutConfirmTitle')}
          message={t('nav.logoutConfirmMsg')}
          confirmText={t('nav.logout')}
          cancelText={t('common.cancel')}
          danger
          loading={loggingOut}
          onConfirm={handleLogout}
          onClose={() => setConfirmingLogout(false)}
        />
      </div>
    </BreadcrumbTargetContext.Provider>
  );
}
