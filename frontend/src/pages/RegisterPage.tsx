import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { TextField } from '../components/ui/Field';
import { registrationApi } from '../api/registration';
import { ApiError } from '../lib/api';
import { cn } from '../lib/cn';

type Status = 'idle' | 'submitting' | 'success';

export function RegisterPage() {
  const [companyName, setCompanyName] = useState('');
  const [subdomain, setSubdomain] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [adminFirstName, setAdminFirstName] = useState('');
  const [adminLastName, setAdminLastName] = useState('');

  const [status, setStatus] = useState<Status>('idle');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [resultSubdomain, setResultSubdomain] = useState<string | null>(null);

  // Subdomain suggestion state
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [suggesting, setSuggesting] = useState(false);

  async function handleSuggest() {
    if (!companyName.trim()) {
      setFieldErrors((prev) => ({ ...prev, companyName: 'Öneri için önce şirket adını girin.' }));
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
    setError(null);
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
      if (err instanceof ApiError) {
        setError(err.body.message);
        const mapped: Record<string, string> = {};
        for (const f of err.body.fields ?? []) {
          if (f.field) mapped[f.field] = f.message;
        }
        setFieldErrors(mapped);
      } else {
        setError('Kayıt sırasında beklenmeyen bir hata oluştu.');
      }
      setStatus('idle');
    }
  }

  if (status === 'success') {
    return (
      <div className="flex min-h-screen items-center justify-center p-4">
        <div className="w-full max-w-sm rounded-2xl border border-glass bg-sidebar/90 p-8 shadow-2xl shadow-black/50 backdrop-blur-md">
          <div className="mb-8 flex flex-col items-center gap-3 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-accent-green/30 to-accent-blue/30 text-2xl text-accent-green shadow-lg shadow-accent-green/20">
              <svg viewBox="0 0 24 24" className="h-7 w-7 fill-none stroke-current stroke-[2.5]" strokeLinecap="round" strokeLinejoin="round">
                <path d="M5 12l5 5L20 7" />
              </svg>
            </div>
            <div>
              <h1 className="text-2xl font-semibold text-main">Doğrulama gönderildi</h1>
              <p className="mt-1 text-sm text-muted">Organizasyon hazırlanıyor</p>
            </div>
          </div>
          <div className="rounded-lg border border-glass bg-white/5 px-4 py-3 text-sm text-muted">
            <p>
              Doğrulama bağlantısı <span className="text-main">{adminEmail}</span> adresine gönderildi.
              E-postanızdaki linke tıklayarak organizasyonu etkinleştirin.
            </p>
            {resultSubdomain && (
              <p className="mt-2">
                Subdomain: <span className="font-mono text-accent">{resultSubdomain}</span>
              </p>
            )}
            <p className="mt-2 text-xs text-muted/70">
              Geliştirme ortamında bağlantı backend konsol log'una da yazılır.
            </p>
          </div>
          <div className="mt-6 flex flex-col gap-3">
            <Link to="/login">
              <Button variant="secondary" className="w-full">Giriş sayfasına dön</Button>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="w-full max-w-md rounded-2xl border border-glass bg-sidebar/90 p-8 shadow-2xl shadow-black/50 backdrop-blur-md">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-accent to-accent-blue text-2xl font-bold text-white shadow-lg shadow-accent/40">
            SF
          </div>
          <div>
            <h1 className="text-2xl font-semibold text-main">ForgeSys</h1>
            <p className="mt-1 text-sm text-muted">Yeni organizasyon oluştur</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <TextField
            id="companyName"
            label="Şirket adı"
            value={companyName}
            onChange={(e) => {
              setCompanyName(e.target.value);
              setSuggestions([]);
            }}
            placeholder="Örn. Acme A.Ş."
            required
            autoComplete="organization"
            error={fieldErrors.companyName ?? null}
          />

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <label htmlFor="subdomain" className="text-xs font-medium uppercase tracking-wide text-muted">
                Subdomain
              </label>
              <button
                type="button"
                onClick={handleSuggest}
                disabled={suggesting}
                className="text-xs font-medium text-accent transition-colors hover:text-accent-blue disabled:opacity-50"
              >
                {suggesting ? 'Öneriliyor...' : 'Öner'}
              </button>
            </div>
            <div className="flex items-center gap-2">
              <input
                id="subdomain"
                type="text"
                value={subdomain}
                onChange={(e) => setSubdomain(e.target.value.toLowerCase())}
                placeholder="acme"
                required
                autoComplete="off"
                className={cn(
                  'w-full rounded-lg border bg-white/5 px-3 py-2 text-sm text-main placeholder:text-muted/50',
                  'transition-colors focus:outline-none focus:ring-2 focus:ring-accent/50',
                  fieldErrors.subdomain ? 'border-danger/50' : 'border-glass',
                )}
              />
              <span className="shrink-0 text-xs text-muted/70">.forgesys</span>
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
                        : 'border-glass bg-white/5 text-muted hover:border-accent/50 hover:text-main',
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
            label="Admin e-posta"
            type="email"
            value={adminEmail}
            onChange={(e) => setAdminEmail(e.target.value)}
            placeholder="admin@sirket.com"
            required
            autoComplete="email"
            error={fieldErrors.adminEmail ?? null}
          />

          <TextField
            id="adminPassword"
            label="Admin şifre"
            type="password"
            value={adminPassword}
            onChange={(e) => setAdminPassword(e.target.value)}
            placeholder="••••••••"
            required
            minLength={8}
            autoComplete="new-password"
            hint="En az 8 karakter"
            error={fieldErrors.adminPassword ?? null}
          />

          <div className="flex gap-3">
            <TextField
              id="adminFirstName"
              label="Ad (opsiyonel)"
              value={adminFirstName}
              onChange={(e) => setAdminFirstName(e.target.value)}
              placeholder="Ad"
              autoComplete="given-name"
              error={fieldErrors.adminFirstName ?? null}
            />
            <TextField
              id="adminLastName"
              label="Soyad (opsiyonel)"
              value={adminLastName}
              onChange={(e) => setAdminLastName(e.target.value)}
              placeholder="Soyad"
              autoComplete="family-name"
              error={fieldErrors.adminLastName ?? null}
            />
          </div>

          {error && (
            <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
              {error}
            </div>
          )}

          <Button type="submit" variant="primary" loading={status === 'submitting'} className="mt-2 w-full">
            {status === 'submitting' ? 'Gönderiliyor...' : 'Organizasyon Oluştur'}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted">
          Zaten hesabın var mı?{' '}
          <Link to="/login" className="font-medium text-accent transition-colors hover:text-accent-blue">
            Giriş yap
          </Link>
        </p>
      </div>
    </div>
  );
}
