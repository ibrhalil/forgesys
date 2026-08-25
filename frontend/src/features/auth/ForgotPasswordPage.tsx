import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { AuthCard } from './components/AuthCard';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { authApi } from './authApi';
import { useT } from '../../lib/i18n';

/**
 * Asks for the account email and fires the reset mail. The backend answers with a
 * uniform 200 regardless of whether the address exists — the SAME info copy is
 * rendered on success (no account enumeration).
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [failed, setFailed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const { t } = useT();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setFailed(false);
    try {
      await authApi.forgotPassword(email);
      setSent(true);
    } catch {
      // Uniform-200 covers the happy paths; rate limiting (429) or a network
      // failure still rejects — surface a retryable error, the form stays up.
      setFailed(true);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthCard title={t('auth.forgot.title')}>
      {sent ? (
        <div className="flex flex-col gap-4 text-center">
          <p className="text-sm text-muted">{t('auth.forgot.sent')}</p>
          <Link to="/login" className="w-full">
            <Button variant="primary" className="w-full">{t('auth.verify.backToLogin')}</Button>
          </Link>
        </div>
      ) : (
        <>
          <p className="text-sm text-muted">{t('auth.forgot.desc')}</p>
          {failed && (
            <p role="alert" className="mt-2 text-sm text-danger">{t('auth.forgot.err.generic')}</p>
          )}
          <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4">
            <TextField
              id="forgot-email"
              label={t('common.email')}
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={t('auth.emailPlaceholder')}
              required
            />
            <Button type="submit" variant="primary" loading={submitting} className="w-full">
              {t('auth.forgot.submit')}
            </Button>
          </form>
          <Link to="/login" className="mt-6 block text-center text-sm text-muted transition-colors hover:text-main">
            {t('auth.forgot.backToLogin')}
          </Link>
        </>
      )}
    </AuthCard>
  );
}
