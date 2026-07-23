import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';

interface RequireAuthProps {
  children: React.ReactNode;
}

export function RequireAuth({ children }: RequireAuthProps) {
  const { isAuthenticated, isLoading } = useAuthStore();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 text-muted">
        <span className="inline-block h-8 w-8 animate-spin rounded-full border-[3px] border-glass border-t-accent" />
        <p>Loading...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
