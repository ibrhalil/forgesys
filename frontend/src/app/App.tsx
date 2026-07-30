import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom';
import {useEffect} from 'react';
import {useAuthStore} from '../store/authStore';
import {AppShell} from '../components/AppShell';
import {RequireAuth} from '../components/RequireAuth';
import {RequirePermission} from '../components/RequirePermission';
import {Spinner} from '../components/ui/Spinner';
import {SHELL_ROUTES} from './Routes.ts';
import {LoginPage} from '../features/auth/LoginPage';
import {RegisterPage} from '../features/auth/RegisterPage';
import {VerifyTenantPage} from '../features/auth/VerifyTenantPage';
import {ApiError} from '../lib/api';
import {notifyApiError} from '../lib/notify';
import {useT} from '../lib/i18n';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        // Don't retry auth failures — a 401/403 won't succeed on repeat.
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          return false;
        }
        return failureCount < 1;
      },
      refetchOnWindowFocus: false,
    },
    mutations: {
      // Global mutation error handler: toasts non-validation failures (network, 403,
      // 404, 409 business rule, 500…). It stays silent on field-level validation
      // (fields[] → handled inline by the form) and on 401 (session-expired redirect).
      onError: (err) => notifyApiError(err),
    },
  },
});

export default function App() {
  // Primitive/action selectors only — subscribing to the whole store would re-render
  // the provider tree on every authStore write.
  const fetchMe = useAuthStore((s) => s.fetchMe);
  const isLoading = useAuthStore((s) => s.isLoading);
  const {t} = useT();

  // Try to fetch current user session on mount
  useEffect(() => {
    fetchMe();
  }, [fetchMe]);

  if (isLoading) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 text-muted">
        <Spinner size="lg" className="border-glass border-t-accent" />
        <p>{t('app.loading')}</p>
      </div>
    );
  }

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage/>}/>
          <Route path="/register" element={<RegisterPage/>}/>
          <Route path="/verify-tenant" element={<VerifyTenantPage/>}/>

          <Route
            path="/"
            element={
              <RequireAuth>
                <AppShell/>
              </RequireAuth>
            }
          >
            {SHELL_ROUTES.map(({path, index, Component, authority}) => {
              const element = authority ? (
                <RequirePermission authority={authority}>
                  <Component/>
                </RequirePermission>
              ) : (
                <Component/>
              );
              return index ? (
                <Route key="__index__" index element={element}/>
              ) : (
                <Route key={path} path={path} element={element}/>
              );
            })}
          </Route>

          <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
