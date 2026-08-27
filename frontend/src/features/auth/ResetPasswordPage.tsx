import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { LuShieldAlert } from 'react-icons/lu';
import { AuthCard } from './components/AuthCard';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { authApi } from './authApi';
import { ApiError } from '../../lib/api';
import { useT, type MessageKey } from '../../lib/i18n';

type State =
  | { kind: 'form' }
  | { kind: 'done' }
  | { kind: 'error'; messageKey: MessageKey };

/** Stable backend ErrorCode -> message key (rendered through t()). */
const ERROR_KEYS: Record<string, MessageKey> = {
  user_token_invalid: 'auth.reset.err.invalid',
  user_token_expired: 'auth.reset.err.expired',
  user_token_already_used: 'auth.reset.err.used',
};

/**
 * Landing page of the emailed password-reset link: reads the token from the query
 * string, collects the new password twice and POSTs it. Success kills all of the
 * user's sessions server-side; the user is sent to /login.
 */
export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [mismatch, setMismatch] = useState(false);
  const [state, setState] = useState<State>(() =>
    token ? { kind: 'form' } : { kind: 'error', messageKey: 'auth.reset.err.noToken' },
  );
  const [submitting, setSubmitting] = useState(false);
  const { t } = useT();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (submitting || !token) return;
    if (password !== confirm) {
      setMismatch(true);
      return;
    }
    setMismatch(false);
    setSubmitting(true);
    try {
      await authApi.resetPassword(token, password);
      setState({ kind: 'done' });
    } catch (err) {
      const code = err instanceof ApiError ? err.code : '';
      setState({ kind: 'error', messageKey: ERROR_KEYS[code] ?? 'auth.reset.err.generic' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthCard title={t('auth.reset.title')}>
      {state.kind === 'form' && (
        <>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <TextField
              id="reset-password"
              label={t('auth.reset.newPassword')}
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={t('common.min8')}
              hint={t('common.min8')}
              required
              minLength={8}
            />
            <TextField
              id="reset-password-confirm"
              label={t('auth.reset.confirm')}
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              error={mismatch ? t('auth.reset.mismatch') : null}
              required
              minLength={8}
            />
            <Button type="submit" variant="primary" loading={submitting} className="w-full">
              {t('auth.reset.submit')}
            </Button>
          </form>
        </>
      )}

      {state.kind === 'done' && (
        <div className="flex flex-col items-center gap-4 text-center">
          <p className="text-sm text-muted">{t('auth.reset.done')}</p>
          <Link to="/login" className="w-full">
            <Button variant="primary" className="w-full">{t('auth.signIn')}</Button>
          </Link>
        </div>
      )}

      {state.kind === 'error' && (
        <div className="flex flex-col items-center gap-4 text-center">
          <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-danger/10 text-2xl text-danger">
            <LuShieldAlert className="h-7 w-7" strokeWidth={2.5} />
          </div>
          <p className="text-sm text-muted">{t(state.messageKey)}</p>
          <Link to="/forgot-password" className="w-full">
            <Button variant="secondary" className="w-full">{t('auth.forgot.title')}</Button>
          </Link>
        </div>
      )}
    </AuthCard>
  );
}
