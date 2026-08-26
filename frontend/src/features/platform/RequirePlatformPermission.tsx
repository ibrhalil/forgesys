import type { ReactNode } from 'react';
import { LuShieldX } from 'react-icons/lu';
import { usePlatformAuthStore } from '../../store/platformAuthStore';
import { useT } from '../../lib/i18n';

interface RequirePlatformPermissionProps {
  authority: string;
  children: ReactNode;
}

/**
 * Route-level permission guard for the platform console — the platform twin of
 * {@link RequirePermission}, reading the PLATFORM session's authorities (a
 * tenant session never satisfies it). The backend still enforces the real
 * security.
 */
export function RequirePlatformPermission({ authority, children }: RequirePlatformPermissionProps) {
  const hasAuthority = usePlatformAuthStore((s) => s.hasAuthority);
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
