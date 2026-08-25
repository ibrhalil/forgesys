import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { LuCheck, LuMailCheck, LuShieldAlert } from 'react-icons/lu';
import { AuthCard } from './components/AuthCard';
import { Button } from '../../components/ui/Button';
import { Spinner } from '../../components/ui/Spinner';
import { authApi } from './authApi';
import { ApiError } from '../../lib/api';
import { useT, type MessageKey } from '../../lib/i18n';

type State =
  | { kind: 'verifying' }
  | { kind: 'success' }
  | { kind: 'error'; messageKey: MessageKey };

/** Stable backend ErrorCode -> message key (rendered through t()). */
const ERROR_KEYS: Record<string, MessageKey> = {
  user_token_invalid: 'auth.emailVerify.err.invalid',
  user_token_expired: 'auth.emailVerify.err.expired',
  user_token_already_used: 'auth.emailVerify.err.used',
};

/**
 * Landing page of the emailed email-verification link (optional-policy flow — the
 * account works either way; this only proves mailbox ownership). The link is
 * subdomain-anchored so TenantFilter resolves the tenant when the token is POSTed.
 */
export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const { t } = useT();
  const [state, setState] = useState<State>(() =>
    token ? { kind: 'verifying' } : { kind: 'error', messageKey: 'auth.emailVerify.err.noToken' },
  );

  const firedRef = useRef(false);
  useEffect(() => {
    if (!token || firedRef.current) return;
    firedRef.current = true;
    (async () => {
      try {
        await authApi.verifyEmail(token);
        // Localized copy — the backend message is fixed Turkish and must not leak
        // into EN locales.
        setState({ kind: 'success' });
      } catch (err) {
        const code = err instanceof ApiError ? err.code : '';
        setState({
          kind: 'error',
          messageKey: ERROR_KEYS[code] ?? 'auth.emailVerify.err.generic',
        });
      }
    })();
  }, [token]);

  return (
    <AuthCard>
      {state.kind === 'verifying' && (
        <div className="flex flex-col items-center gap-4 text-center">
          <Spinner size="lg" className="border-glass border-t-accent" />
          <div>
            <h1 className="text-xl font-semibold text-main">{t('auth.emailVerify.verifyingTitle')}</h1>
            <p className="mt-1 text-sm text-muted">{t('auth.emailVerify.verifyingDesc')}</p>
          </div>
        </div>
      )}

      {state.kind === 'success' && (
        <div className="flex flex-col items-center gap-4 text-center">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-accent-green/30 to-accent-blue/30 text-2xl text-accent-green shadow-lg shadow-accent-green/20">
            <LuCheck className="h-7 w-7" strokeWidth={2.5} />
          </div>
          <div>
            <h1 className="text-2xl font-semibold text-main">{t('auth.emailVerify.successTitle')}</h1>
            <p className="mt-1 flex items-center justify-center gap-2 text-sm text-muted">
              <LuMailCheck className="h-4 w-4" aria-hidden />
              {t('auth.emailVerify.success')}
            </p>
          </div>
          <Link to="/login" className="w-full">
            <Button variant="primary" className="w-full">{t('auth.verify.backToLogin')}</Button>
          </Link>
        </div>
      )}

      {state.kind === 'error' && (
        <div className="flex flex-col items-center gap-4 text-center">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-danger/30 to-danger/10 text-2xl text-danger shadow-lg shadow-danger/20">
            <LuShieldAlert className="h-7 w-7" strokeWidth={2.5} />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-main">{t('auth.emailVerify.failedTitle')}</h1>
            <p className="mt-1 text-sm text-muted">{t(state.messageKey)}</p>
          </div>
          <Link to="/login" className="w-full">
            <Button variant="secondary" className="w-full">{t('auth.verify.backToLogin')}</Button>
          </Link>
        </div>
      )}
    </AuthCard>
  );
}
