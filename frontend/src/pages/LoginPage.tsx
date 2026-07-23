import { useState, type FormEvent } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useTenantStore } from '../store/tenantStore';
import { Button } from '../components/ui/Button';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [tenant, setTenant] = useState(useTenantStore.getState().tenantId ?? '');
  const { login, isSubmitting, error, clearError } = useAuthStore();
  const { setTenantId } = useTenantStore();
  const navigate = useNavigate();
  const location = useLocation();

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname ?? '/';

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    clearError();
    if (tenant) setTenantId(tenant);
    const success = await login(email, password);
    if (success) navigate(from, { replace: true });
  };

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-2xl border border-glass bg-sidebar/90 p-8 shadow-2xl shadow-black/50 backdrop-blur-md">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-accent to-accent-blue text-2xl font-bold text-white shadow-lg shadow-accent/40">
            SF
          </div>
          <div>
            <h1 className="text-2xl font-semibold text-main">ForgeSys</h1>
            <p className="mt-1 text-sm text-muted">Multi-tenant SaaS Platform</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="tenant" className="text-xs font-medium uppercase tracking-wide text-muted">
              Tenant (subdomain)
            </label>
            <input
              id="tenant"
              type="text"
              value={tenant}
              onChange={(e) => setTenant(e.target.value)}
              placeholder="e.g. acme, system"
              autoComplete="organization"
              className="w-full rounded-lg border border-glass bg-white/5 px-3 py-2 text-sm text-main placeholder:text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="email" className="text-xs font-medium uppercase tracking-wide text-muted">
              Email
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@company.com"
              required
              autoComplete="email"
              className="w-full rounded-lg border border-glass bg-white/5 px-3 py-2 text-sm text-main placeholder:text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="password" className="text-xs font-medium uppercase tracking-wide text-muted">
              Password
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              autoComplete="current-password"
              className="w-full rounded-lg border border-glass bg-white/5 px-3 py-2 text-sm text-main placeholder:text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>

          {error && (
            <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
              {error}
            </div>
          )}

          <Button type="submit" variant="primary" loading={isSubmitting} className="mt-2 w-full">
            {isSubmitting ? 'Signing in...' : 'Sign In'}
          </Button>
        </form>
      </div>
    </div>
  );
}
