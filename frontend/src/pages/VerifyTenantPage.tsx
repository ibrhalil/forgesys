import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { registrationApi } from '../api/registration';
import { useTenantStore } from '../store/tenantStore';
import { ApiError } from '../lib/api';
import type { VerifyTenantResponse } from '../types';

type State =
  | { kind: 'verifying' }
  | { kind: 'success'; data: VerifyTenantResponse }
  | { kind: 'error'; message: string };

const ERROR_MESSAGES: Record<string, string> = {
  tenant_token_invalid: 'Doğrulama bağlantısı geçersiz veya bilinmiyor.',
  tenant_token_expired: 'Doğrulama bağlantısının süresi doldu. Yeni bir kayıt başlatın.',
  tenant_token_already_used: 'Bu doğrulama bağlantısı zaten kullanıldı.',
};

export function VerifyTenantPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [state, setState] = useState<State>(() =>
    token ? { kind: 'verifying' } : { kind: 'error', message: 'Doğrulama bağlantısında token bulunamadı.' },
  );
  const { setTenantId } = useTenantStore();

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
          message: ERROR_MESSAGES[code] ?? 'Doğrulama sırasında bir hata oluştu.',
        });
      }
    })();
  }, [token]);

  // Card shell matching LoginPage/RegisterPage
  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-2xl border border-glass bg-sidebar/90 p-8 shadow-2xl shadow-black/50 backdrop-blur-md">
        {state.kind === 'verifying' && (
          <div className="flex flex-col items-center gap-4 text-center">
            <span className="inline-block h-8 w-8 animate-spin rounded-full border-[3px] border-glass border-t-accent" />
            <div>
              <h1 className="text-xl font-semibold text-main">Organizasyon etkinleştiriliyor</h1>
              <p className="mt-1 text-sm text-muted">
                Şema, veritabanı ve admin kullanıcısı hazırlanıyor. Bu birkaç saniye sürebilir.
              </p>
            </div>
          </div>
        )}

        {state.kind === 'success' && (
          <div className="flex flex-col items-center gap-4 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-accent-green/30 to-accent-blue/30 text-2xl text-accent-green shadow-lg shadow-accent-green/20">
              <svg viewBox="0 0 24 24" className="h-7 w-7 fill-none stroke-current stroke-[2.5]" strokeLinecap="round" strokeLinejoin="round">
                <path d="M5 12l5 5L20 7" />
              </svg>
            </div>
            <div>
              <h1 className="text-2xl font-semibold text-main">{state.data.name}</h1>
              <p className="mt-1 text-sm text-muted">{state.data.message ?? 'Organizasyon etkinleştirildi.'}</p>
            </div>
            <div className="w-full rounded-lg border border-glass bg-white/5 px-4 py-3 text-sm text-muted">
              <p>
                Subdomain:{' '}
                <span className="font-mono text-accent">{state.data.subdomain}</span>
              </p>
              <p className="mt-1 text-xs text-muted/70">Artık admin olarak giriş yapabilirsiniz.</p>
            </div>
            <Link
              to="/login"
              onClick={() => setTenantId(state.data.subdomain)}
              className="w-full"
            >
              <Button variant="primary" className="w-full">Yönetici olarak giriş yap</Button>
            </Link>
          </div>
        )}

        {state.kind === 'error' && (
          <div className="flex flex-col items-center gap-4 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-danger/30 to-danger/10 text-2xl text-danger shadow-lg shadow-danger/20">
              <svg viewBox="0 0 24 24" className="h-7 w-7 fill-none stroke-current stroke-[2.5]" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 8v5M12 16.5v.5M7.5 4h9l4 7-7 9h-3l-7-9 4-7z" />
              </svg>
            </div>
            <div>
              <h1 className="text-xl font-semibold text-main">Doğrulama başarısız</h1>
              <p className="mt-1 text-sm text-muted">{state.message}</p>
            </div>
            <Link to="/register" className="w-full">
              <Button variant="secondary" className="w-full">Yeni kayıt başlat</Button>
            </Link>
            <Link to="/login" className="text-sm text-muted transition-colors hover:text-main">
              Giriş sayfasına dön
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
