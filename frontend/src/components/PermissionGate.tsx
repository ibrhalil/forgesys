import { useAuthStore } from '../store/authStore';

interface PermissionGateProps {
  authority: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export function PermissionGate({ authority, children, fallback = null }: PermissionGateProps) {
  const hasAuthority = useAuthStore((s) => s.hasAuthority);

  if (!hasAuthority(authority)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
