import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { AuthCard } from './components/AuthCard';
import { useAuthStore } from '../../store/authStore';
import { useTenantStore } from '../../store/tenantStore';
import { Button } from '../../components/ui/Button';
import { useT } from '../../lib/i18n';
import { INPUT_BASE } from '../../components/ui/styles';

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
        <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-accent text-2xl font-bold text-white shadow-sm shadow-accent/25">
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
              className={INPUT_BASE}
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
              className={INPUT_BASE}
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
              className={INPUT_BASE}
            />
          </div>

          <Button type="submit" variant="primary" loading={isSubmitting} className="mt-2 w-full">
            {isSubmitting ? t('auth.signingIn') : t('auth.signIn')}
          </Button>
        </form>

        <Link to="/forgot-password" className="mt-3 block text-center text-sm text-muted transition-colors hover:text-main">
          {t('auth.forgot.link')}
        </Link>

        <p className="mt-6 text-center text-sm text-muted">
          {t('auth.newOrg')}{' '}
          <Link to="/register" className="font-medium text-accent transition-colors hover:text-accent-blue">
            {t('auth.registerLink')}
          </Link>
        </p>
    </AuthCard>
  );
}
