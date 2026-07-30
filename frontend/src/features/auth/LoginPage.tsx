import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { AuthCard } from './components/AuthCard';
import { useAuthStore } from '../../store/authStore';
import { useTenantStore } from '../../store/tenantStore';
import { Button } from '../../components/ui/Button';
import { useT } from '../../lib/i18n';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [tenant, setTenant] = useState(useTenantStore.getState().tenantId ?? '');
  const login = useAuthStore((s) => s.login);
  const isSubmitting = useAuthStore((s) => s.isSubmitting);
  const setTenantId = useTenantStore((s) => s.setTenantId);
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useT();

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname ?? '/';

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (tenant) setTenantId(tenant);
    const success = await login(email, password);
    if (success) navigate(from, { replace: true });
  };

  return (
    <AuthCard
      title="ForgeSys"
      subtitle={t('auth.subtitle')}
      icon={(
        <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-accent to-accent-blue text-2xl font-bold text-white shadow-lg shadow-accent/40">
          SF
        </div>
      )}
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="tenant" className="text-xs font-medium uppercase tracking-wide text-muted">
              {t('auth.tenantLabel')}
            </label>
            <input
              id="tenant"
              type="text"
              value={tenant}
              onChange={(e) => setTenant(e.target.value)}
              placeholder={t('auth.tenantPlaceholder')}
              autoComplete="organization"
              className="w-full rounded-lg border border-glass bg-main/5 px-3 py-2 text-sm text-main placeholder:text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="email" className="text-xs font-medium uppercase tracking-wide text-muted">
              {t('auth.emailLabel')}
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={t('auth.emailPlaceholder')}
              required
              autoComplete="email"
              className="w-full rounded-lg border border-glass bg-main/5 px-3 py-2 text-sm text-main placeholder:text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="password" className="text-xs font-medium uppercase tracking-wide text-muted">
              {t('auth.passwordLabel')}
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              autoComplete="current-password"
              className="w-full rounded-lg border border-glass bg-main/5 px-3 py-2 text-sm text-main placeholder:text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>

          <Button type="submit" variant="primary" loading={isSubmitting} className="mt-2 w-full">
            {isSubmitting ? t('auth.signingIn') : t('auth.signIn')}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted">
          {t('auth.newOrg')}{' '}
          <Link to="/register" className="font-medium text-accent transition-colors hover:text-accent-blue">
            {t('auth.registerLink')}
          </Link>
        </p>
    </AuthCard>
  );
}
