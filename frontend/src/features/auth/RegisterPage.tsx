import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { LuCheck } from 'react-icons/lu';
import { AuthCard } from './components/AuthCard';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { registrationApi } from './registrationApi';
import { notify, extractFieldErrors, errorMessage } from '../../lib/notify';
import { useT } from '../../lib/i18n';
import { cn } from '../../lib/cn';
import { INPUT_BASE } from '../../components/ui/styles';

type Status = 'idle' | 'submitting' | 'success';

export function RegisterPage() {
  const [companyName, setCompanyName] = useState('');
  const [subdomain, setSubdomain] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [adminFirstName, setAdminFirstName] = useState('');
  const [adminLastName, setAdminLastName] = useState('');

  const { t } = useT();
  const [status, setStatus] = useState<Status>('idle');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [resultSubdomain, setResultSubdomain] = useState<string | null>(null);

  // Subdomain suggestion state
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [suggesting, setSuggesting] = useState(false);

  async function handleSuggest() {
    if (!companyName.trim()) {
      setFieldErrors((prev) => ({ ...prev, companyName: t('auth.register.suggestNeedName') }));
      return;
    }
    setSuggesting(true);
    try {
      const res = await registrationApi.suggestSubdomain({ name: companyName });
      setSuggestions(res.suggestions ?? []);
    } catch {
      setSuggestions([]);
    } finally {
      setSuggesting(false);
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFieldErrors({});
    setStatus('submitting');
    try {
      const res = await registrationApi.register({
        companyName,
        subdomain,
        adminEmail,
        adminPassword,
        adminFirstName: adminFirstName || undefined,
        adminLastName: adminLastName || undefined,
      });
      setResultSubdomain(res.subdomain);
      setStatus('success');
    } catch (err) {
      const fe = extractFieldErrors(err);
      setFieldErrors(fe);
      // Field validation renders inline; any other failure surfaces as a toast.
      if (Object.keys(fe).length === 0) notify.error(errorMessage(err));
      setStatus('idle');
    }
  }

  if (status === 'success') {
    return (
      <AuthCard
        title={t('auth.register.successTitle')}
        subtitle={t('auth.register.successSubtitle')}
        icon={(
          <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-accent-green/10 text-2xl text-accent-green">
            <LuCheck className="h-7 w-7" strokeWidth={2.5} />
          </div>
        )}
      >
        <div className="rounded-lg border border-glass bg-main/5 px-4 py-3 text-sm text-muted">
          <p>{t('auth.register.successBody', { email: adminEmail })}</p>
          {resultSubdomain && (
            <p className="mt-2">
              {t('auth.subdomainLabel')}: <span className="font-mono text-accent">{resultSubdomain}</span>
            </p>
          )}
          <p className="mt-2 text-xs text-muted/70">
            {t('auth.register.devNote')}
          </p>
        </div>
        <div className="mt-6 flex flex-col gap-3">
          <Link to="/login">
            <Button variant="secondary" className="w-full">{t('auth.verify.backToLogin')}</Button>
          </Link>
        </div>
      </AuthCard>
    );
  }

  return (
    <AuthCard
      size="md"
      title="ForgeSys"
      subtitle={t('auth.register.title')}
      icon={(
        <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-accent text-2xl font-bold text-white shadow-sm shadow-accent/25">
          SF
        </div>
      )}
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <TextField
            id="companyName"
            label={t('auth.register.companyName')}
            value={companyName}
            onChange={(e) => {
              setCompanyName(e.target.value);
              setSuggestions([]);
            }}
            placeholder={t('auth.register.companyNamePlaceholder')}
            required
            autoComplete="organization"
            error={fieldErrors.companyName ?? null}
          />

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <label htmlFor="subdomain" className="text-xs font-medium uppercase tracking-wide text-muted">
                {t('auth.register.subdomainLabel')}
              </label>
              <button
                type="button"
                onClick={handleSuggest}
                disabled={suggesting}
                className="text-xs font-medium text-accent transition-colors hover:text-accent-blue disabled:opacity-50"
              >
                {suggesting ? t('auth.register.suggesting') : t('auth.register.suggest')}
              </button>
            </div>
            <div className="flex items-center gap-2">
              <input
                id="subdomain"
                type="text"
                value={subdomain}
                onChange={(e) => setSubdomain(e.target.value.toLowerCase())}
                placeholder={t('auth.register.subdomainPlaceholder')}
                required
                autoComplete="off"
                className={cn(
                  INPUT_BASE,
                  'transition-colors focus:outline-none focus:ring-2 focus:ring-accent/50',
                  fieldErrors.subdomain ? 'border-danger/50' : 'border-glass',
                )}
              />
              <span className="shrink-0 text-xs text-muted/70">{t('auth.register.subdomainSuffix')}</span>
            </div>
            {fieldErrors.subdomain && (
              <span className="text-xs text-danger">{fieldErrors.subdomain}</span>
            )}
            {suggestions.length > 0 && (
              <div className="mt-1 flex flex-wrap gap-1.5">
                {suggestions.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setSubdomain(s)}
                    className={cn(
                      'rounded-full border px-2.5 py-0.5 font-mono text-xs transition-colors',
                      subdomain === s
                        ? 'border-accent bg-accent/20 text-accent'
                        : 'border-glass bg-main/5 text-muted hover:border-accent/50 hover:text-main',
                    )}
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
          </div>

          <TextField
            id="adminEmail"
            label={t('auth.register.adminEmail')}
            type="email"
            value={adminEmail}
            onChange={(e) => setAdminEmail(e.target.value)}
            placeholder={t('auth.adminEmailPh')}
            required
            autoComplete="email"
            error={fieldErrors.adminEmail ?? null}
          />

          <TextField
            id="adminPassword"
            label={t('auth.register.adminPassword')}
            type="password"
            value={adminPassword}
            onChange={(e) => setAdminPassword(e.target.value)}
            placeholder="••••••••"
            required
            minLength={8}
            autoComplete="new-password"
            hint={t('auth.register.passwordHint')}
            error={fieldErrors.adminPassword ?? null}
          />

          <div className="flex gap-3">
            <TextField
              id="adminFirstName"
              label={t('auth.register.firstName')}
              value={adminFirstName}
              onChange={(e) => setAdminFirstName(e.target.value)}
              placeholder={t('auth.register.firstNamePlaceholder')}
              autoComplete="given-name"
              error={fieldErrors.adminFirstName ?? null}
            />
            <TextField
              id="adminLastName"
              label={t('auth.register.lastName')}
              value={adminLastName}
              onChange={(e) => setAdminLastName(e.target.value)}
              placeholder={t('auth.register.lastNamePlaceholder')}
              autoComplete="family-name"
              error={fieldErrors.adminLastName ?? null}
            />
          </div>

          <Button type="submit" variant="primary" loading={status === 'submitting'} className="mt-2 w-full">
            {status === 'submitting' ? t('auth.register.submitting') : t('auth.register.submit')}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted">
          {t('auth.register.hasAccount')}{' '}
          <Link to="/login" className="font-medium text-accent transition-colors hover:text-accent-blue">
            {t('auth.register.loginLink')}
          </Link>
        </p>
    </AuthCard>
  );
}
