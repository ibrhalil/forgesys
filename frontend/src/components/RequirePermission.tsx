import type { ReactNode } from 'react';
import { LuShieldX } from 'react-icons/lu';
import { useAuthStore } from '../store/authStore';
import { useT } from '../lib/i18n';

interface RequirePermissionProps {
  authority: string;
  children: ReactNode;
}

/**
 * Route-level permission guard: renders the page only when the user holds the
 * authority; otherwise an inline "access denied" state inside the app shell (no
 * redirect — a redirect could loop when the target route is gated too). The backend
 * still enforces the real security.
 */
export function RequirePermission({ authority, children }: RequirePermissionProps) {
  const hasAuthority = useAuthStore((s) => s.hasAuthority);
  const { t } = useT();

  if (!hasAuthority(authority)) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
        <LuShieldX size={40} className="text-muted/50" />
        <h1 className="m-0 text-lg font-semibold text-main">{t('common.forbiddenTitle')}</h1>
        <p className="m-0 text-sm text-muted">{t('common.forbiddenDesc')}</p>
      </div>
    );
  }

  return <>{children}</>;
}
