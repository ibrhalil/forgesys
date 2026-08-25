import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom';
import {lazy, useEffect} from 'react';
import {useAuthStore} from '../store/authStore';
import {AppShell} from '../components/AppShell';
import {RequireAuth} from '../components/RequireAuth';
import {RequirePermission} from '../components/RequirePermission';
import {Spinner} from '../components/ui/Spinner';
import {SHELL_ROUTES} from './Routes.ts';
import {LoginPage} from '../features/auth/LoginPage';
import {RegisterPage} from '../features/auth/RegisterPage';
import {VerifyTenantPage} from '../features/auth/VerifyTenantPage';
import {VerifyEmailPage} from '../features/auth/VerifyEmailPage';
import {ForgotPasswordPage} from '../features/auth/ForgotPasswordPage';
import {ResetPasswordPage} from '../features/auth/ResetPasswordPage';
import {ApiError} from '../lib/api';
import {notifyApiError} from '../lib/notify';
import {useT} from '../lib/i18n';

// DEV-only: UI component showcase — tree-shaken in production builds.
const DemoLayout = import.meta.env.DEV
  ? lazy(() => import('../features/demo/DemoLayout').then((m) => ({ default: m.DemoLayout })))
  : null;
const DataTableDemoPage = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/DataTableDemoPage').then((m) => ({ default: m.DataTableDemoPage })))
  : null;
const BadgeDemoPage = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/BadgeDemoPage').then((m) => ({ default: m.BadgeDemoPage })))
  : null;
const ButtonDemoPage = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/ButtonDemoPage').then((m) => ({ default: m.ButtonDemoPage })))
  : null;
const ModalDemoPage = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/ModalDemoPage').then((m) => ({ default: m.ModalDemoPage })))
  : null;
const FormDemoPage = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/FormDemoPage').then((m) => ({ default: m.FormDemoPage })))
  : null;
const RowMenuDemoPage = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/RowMenuDemoPage').then((m) => ({ default: m.RowMenuDemoPage })))
  : null;
const ListPagePatternDemo = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/patterns/ListPagePatternDemo').then((m) => ({ default: m.ListPagePatternDemo })))
  : null;
const DetailPagePatternDemo = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/patterns/DetailPagePatternDemo').then((m) => ({ default: m.DetailPagePatternDemo })))
  : null;
const FormModalPatternDemo = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/patterns/FormModalPatternDemo').then((m) => ({ default: m.FormModalPatternDemo })))
  : null;
const DashboardPatternDemo = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/patterns/DashboardPatternDemo').then((m) => ({ default: m.DashboardPatternDemo })))
  : null;
const MasterDetailPatternDemo = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/patterns/MasterDetailPatternDemo').then((m) => ({ default: m.MasterDetailPatternDemo })))
  : null;
const ThemeDemoPage = import.meta.env.DEV
  ? lazy(() => import('../features/demo/pages/ThemeDemoPage').then((m) => ({ default: m.ThemeDemoPage })))
  : null;





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
          <Route path="/verify-email" element={<VerifyEmailPage/>}/>
          <Route path="/forgot-password" element={<ForgotPasswordPage/>}/>
          <Route path="/reset-password" element={<ResetPasswordPage/>}/>

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

          {import.meta.env.DEV && DemoLayout && (
            <Route path="/demo" element={<DemoLayout />}>
              {ThemeDemoPage && <Route path="themes" element={<ThemeDemoPage />} />}
              {DataTableDemoPage && <Route path="datatable" element={<DataTableDemoPage />} />}
              {BadgeDemoPage && <Route path="badge" element={<BadgeDemoPage />} />}
              {ButtonDemoPage && <Route path="button" element={<ButtonDemoPage />} />}
              {FormDemoPage && <Route path="form" element={<FormDemoPage />} />}
              {ModalDemoPage && <Route path="modal" element={<ModalDemoPage />} />}
              {RowMenuDemoPage && <Route path="rowmenu" element={<RowMenuDemoPage />} />}
              {ListPagePatternDemo && <Route path="patterns/list" element={<ListPagePatternDemo />} />}
              {DetailPagePatternDemo && <Route path="patterns/detail" element={<DetailPagePatternDemo />} />}
              {FormModalPatternDemo && <Route path="patterns/form" element={<FormModalPatternDemo />} />}
              {DashboardPatternDemo && <Route path="patterns/dashboard" element={<DashboardPatternDemo />} />}
              {MasterDetailPatternDemo && <Route path="patterns/master-detail" element={<MasterDetailPatternDemo />} />}
            </Route>
          )}

          <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
