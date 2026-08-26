import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LuUserRoundCog } from 'react-icons/lu';
import { useAuthStore } from '../store/authStore';
import { Button } from './ui/Button';
import { useT } from '../lib/i18n';

/**
 * Impersonation strip for the tenant shell (K-50 F6/F7): rendered when the
 * tenant `/me` reports impersonation info — the platform superadmin is working
 * inside the tenant as its earliest admin. Window title carries an `[imp]`
 * marker while active. "Çıkış" ends the impersonation via the normal logout
 * (backend blacklists the impersonation jti + clears the switch guard).
 */
export function ImpersonationBanner() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const impersonation = user?.impersonation ?? null;
  const navigate = useNavigate();
  const { t } = useT();
  const [exiting, setExiting] = useState(false);

  useEffect(() => {
    if (!impersonation) return;
    const original = document.title;
    document.title = `[imp] ${original}`;
    return () => {
      document.title = original;
    };
  }, [impersonation]);

  if (!impersonation) return null;

  return (
    <div
      data-testid="impersonation-banner"
      className="flex shrink-0 items-center gap-3 border-b border-warning/30 bg-warning/10 px-6 py-2 lg:px-10"
    >
      <LuUserRoundCog className="h-4 w-4 shrink-0 text-warning" aria-hidden />
      <span className="min-w-0 flex-1 truncate text-sm text-main">
        {t('impersonation.banner', { email: impersonation.actorEmail })}
      </span>
      <Button
        size="sm"
        variant="danger"
        loading={exiting}
        onClick={async () => {
          setExiting(true);
          try {
            await logout();
            navigate('/login');
          } finally {
            setExiting(false);
          }
        }}
      >
        {t('impersonation.exit')}
      </Button>
    </div>
  );
}
