import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { LuCheck, LuShieldAlert } from 'react-icons/lu';
import { AuthCard } from './components/AuthCard';
import { Button } from '../../components/ui/Button';
import { Spinner } from '../../components/ui/Spinner';
import { registrationApi } from './registrationApi';
import { useTenantStore } from '../../store/tenantStore';
import { ApiError } from '../../lib/api';
import { useT, type MessageKey } from '../../lib/i18n';
import type { VerifyTenantResponse } from './types';

type State =
  | { kind: 'verifying' }
  | { kind: 'success'; data: VerifyTenantResponse }
  | { kind: 'error'; messageKey: MessageKey };

/** Stable backend ErrorCode -> message key (rendered through t()). */
const ERROR_KEYS: Record<string, MessageKey> = {
  tenant_token_invalid: 'auth.verify.err.invalid',
  tenant_token_expired: 'auth.verify.err.expired',
  tenant_token_already_used: 'auth.verify.err.used',
};

export function VerifyTenantPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const { t } = useT();
  const [state, setState] = useState<State>(() =>
    token ? { kind: 'verifying' } : { kind: 'error', messageKey: 'auth.verify.err.noToken' },
  );
  const setTenantId = useTenantStore((s) => s.setTenantId);

  const firedRef = useRef(false);
  useEffect(() => {
    if (!token || firedRef.current) return;
    firedRef.current = true;
    (async () => {
      try {
        const data = await registrationApi.verify({ token });
        setState({ kind: 'success', data });
      } catch (err) {
        const code = err instanceof ApiError ? err.code : '';
        setState({
          kind: 'error',
          messageKey: ERROR_KEYS[code] ?? 'auth.verify.err.generic',
        });
      }
    })();
  }, [token]);

  // Card shell matching LoginPage/RegisterPage
  return (
    <AuthCard>
        {state.kind === 'verifying' && (
          <div className="flex flex-col items-center gap-4 text-center">
            <Spinner size="lg" className="border-glass border-t-accent" />
            <div>
              <h1 className="text-xl font-semibold text-main">{t('auth.verify.activatingTitle')}</h1>
              <p className="mt-1 text-sm text-muted">
                {t('auth.verify.activatingDesc')}
              </p>
            </div>
          </div>
        )}

        {state.kind === 'success' && (
          <div className="flex flex-col items-center gap-4 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-accent-green/10 text-2xl text-accent-green">
              <LuCheck className="h-7 w-7" strokeWidth={2.5} />
            </div>
            <div>
              <h1 className="text-2xl font-semibold text-main">{state.data.name}</h1>
              <p className="mt-1 text-sm text-muted">{state.data.message ?? t('auth.verify.successFallback')}</p>
            </div>
            <div className="w-full rounded-lg border border-glass bg-main/5 px-4 py-3 text-sm text-muted">
              <p>
                {t('auth.verify.subdomainLabel')}{' '}
                <span className="font-mono text-accent">{state.data.subdomain}</span>
              </p>
              <p className="mt-1 text-xs text-muted/70">{t('auth.verify.canLogin')}</p>
            </div>
            <Link
              to="/login"
              onClick={() => setTenantId(state.data.subdomain)}
              className="w-full"
            >
              <Button variant="primary" className="w-full">{t('auth.verify.loginAsAdmin')}</Button>
            </Link>
          </div>
        )}

        {state.kind === 'error' && (
          <div className="flex flex-col items-center gap-4 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-danger/10 text-2xl text-danger">
              <LuShieldAlert className="h-7 w-7" strokeWidth={2.5} />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-main">{t('auth.verify.failedTitle')}</h1>
              <p className="mt-1 text-sm text-muted">{t(state.messageKey)}</p>
            </div>
            <Link to="/register" className="w-full">
              <Button variant="secondary" className="w-full">{t('auth.verify.startNew')}</Button>
            </Link>
            <Link to="/login" className="text-sm text-muted transition-colors hover:text-main">
              {t('auth.verify.backToLogin')}
            </Link>
          </div>
        )}
    </AuthCard>
  );
}
