import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { PermissionGate } from './PermissionGate';
import { cn } from '../lib/cn';

const navBase =
  'flex items-center gap-3 rounded-lg px-4 py-2.5 text-sm font-medium transition-colors';
const navInactive = 'text-muted hover:bg-white/5 hover:text-main';
const navActive = 'bg-accent/15 text-accent';

function navClass({ isActive }: { isActive: boolean }) {
  return cn(navBase, isActive ? navActive : navInactive);
}

const iconClass = 'h-[18px] w-[18px]';

export function AppShell() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="grid min-h-screen grid-cols-1 lg:grid-cols-[260px_1fr]">
      <aside className="flex flex-col border-b border-glass bg-sidebar p-6 backdrop-blur lg:border-b-0 lg:border-r">
        <div className="mb-10 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-accent to-accent-blue text-lg font-bold text-white shadow-lg shadow-accent/40">
            SF
          </div>
          <h2 className="m-0 text-xl font-semibold tracking-tight text-white">SystemForge</h2>
        </div>

        <nav className="flex flex-1 flex-col gap-2">
          <NavLink to="/" end className={navClass}>
            <svg className={iconClass} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="7" height="9" rx="1" /><rect x="14" y="3" width="7" height="5" rx="1" /><rect x="14" y="12" width="7" height="9" rx="1" /><rect x="3" y="16" width="7" height="5" rx="1" /></svg>
            Dashboard
          </NavLink>

          <PermissionGate authority="iam:user:read">
            <NavLink to="/users" className={navClass}>
              <svg className={iconClass} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></svg>
              Users
            </NavLink>
          </PermissionGate>

          <PermissionGate authority="iam:role:read">
            <NavLink to="/roles" className={navClass}>
              <svg className={iconClass} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg>
              Roles
            </NavLink>
          </PermissionGate>

          <PermissionGate authority="iam:group:read">
            <NavLink to="/groups" className={navClass}>
              <svg className={iconClass} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /></svg>
              Groups
            </NavLink>
          </PermissionGate>
        </nav>

        <div className="mt-auto flex items-center justify-between gap-3 border-t border-glass pt-5">
          <span className="overflow-hidden text-ellipsis whitespace-nowrap text-sm text-main">
            {user?.email ?? '—'}
          </span>
          <button
            onClick={handleLogout}
            className="shrink-0 rounded-md border border-glass px-3 py-1 text-xs text-muted transition-colors hover:border-accent hover:text-accent"
          >
            Logout
          </button>
        </div>
      </aside>

      <main className="box-border overflow-y-auto p-6 lg:p-10">
        <Outlet />
      </main>
    </div>
  );
}
