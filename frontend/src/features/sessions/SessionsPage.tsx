import { useMySessions, useRevokeMySession } from './hooks';
import { SessionList, type SessionRow } from './components/SessionList';
import { Page } from '../../components/Page';
import { useT } from '../../lib/i18n';

/**
 * Self-service active-session view (K-28). Any authenticated user can see where they
 * are logged in and end an individual session. The current device (behind the httpOnly
 * refresh cookie) is flagged "This device"; revoking it signs this browser out
 * immediately (the access token is invalidated on the next request).
 */
export function SessionsPage() {
  const { t } = useT();
  const { data, isLoading } = useMySessions();
  const revoke = useRevokeMySession();

  const onRevoke = (session: SessionRow) => {
    revoke.mutate(session.sessionId);
  };

  return (
    <Page
      breadcrumb={[{ label: t('nav.security') }, { label: t('nav.sessions') }]}
      title={t('nav.sessions')}
      description={t('sessions.desc')}
    >

      <SessionList
        sessions={data ?? []}
        loading={isLoading}
        revokingSessionId={revoke.isPending ? revoke.variables ?? null : null}
        onRevoke={onRevoke}
      />
    </Page>
  );
}
