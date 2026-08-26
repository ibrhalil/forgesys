import { Navigate, useLocation } from 'react-router-dom';
import { usePlatformAuthStore } from '../store/platformAuthStore';
import { Spinner } from './ui/Spinner';
import { useT } from '../lib/i18n';

interface RequirePlatformAuthProps {
  children: React.ReactNode;
}

/** Platform-console twin of {@link RequireAuth}: gates the /platform subtree on the platform session. */
export function RequirePlatformAuth({ children }: RequirePlatformAuthProps) {
  const isAuthenticated = usePlatformAuthStore((s) => s.isAuthenticated);
  const isLoading = usePlatformAuthStore((s) => s.isLoading);
  const location = useLocation();
  const { t } = useT();

  if (isLoading) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 text-muted">
        <Spinner size="lg" className="border-glass border-t-accent" />
        <p>{t('app.loading')}</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/platform/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
