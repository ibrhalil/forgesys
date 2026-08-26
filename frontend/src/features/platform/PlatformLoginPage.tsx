import { useState, type FormEvent } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { usePlatformAuthStore } from '../../store/platformAuthStore';
import { Button } from '../../components/ui/Button';
import { useT } from '../../lib/i18n';

/**
 * Platform console sign-in (K-50): global platform identities only — no tenant
 * field (the platform API is tenant-less), cookies are the platform-scoped
 * {@code sf_platform_*} pair.
 */
export function PlatformLoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const login = usePlatformAuthStore((s) => s.login);
  const isSubmitting = usePlatformAuthStore((s) => s.isSubmitting);
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useT();

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname ?? '/platform';

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
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
            <h1 className="text-2xl font-semibold text-main">{t('platform.loginTitle')}</h1>
            <p className="mt-1 text-sm text-muted">{t('platform.loginSubtitle')}</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="platform-email" className="text-xs font-medium uppercase tracking-wide text-muted">
              {t('platform.emailLabel')}
            </label>
            <input
              id="platform-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
              className="w-full rounded-lg border border-glass bg-main/5 px-3 py-2 text-sm text-main placeholder:text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="platform-password" className="text-xs font-medium uppercase tracking-wide text-muted">
              {t('platform.passwordLabel')}
            </label>
            <input
              id="platform-password"
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
            {isSubmitting ? t('platform.signingIn') : t('platform.signIn')}
          </Button>
        </form>
      </div>
    </div>
  );
}
