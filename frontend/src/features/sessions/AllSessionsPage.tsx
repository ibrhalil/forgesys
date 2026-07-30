import { useAllSessions, useRevokeUserSession } from './hooks';
import { SessionList, type SessionRow } from './components/SessionList';
import { Page } from '../../components/Page';
import { useT } from '../../lib/i18n';

/**
 * Tenant-wide "all sessions" admin view (iam:user:write). Lists every active
 * refresh-token session across all users of the tenant — each row shows its owner
 * (email) so the admin can see who is signed in where. Revoking a row ends that user's
 * session via the per-user admin endpoint (the device is signed out on its next
 * request). Distinct from the self {@code /sessions} page.
 */
export function AllSessionsPage() {
  const { t } = useT();
  const { data, isLoading } = useAllSessions();
  const revoke = useRevokeUserSession();

  const rows: SessionRow[] = (data ?? []).map((s) => ({
    sessionId: s.sessionId,
    userAgent: s.userAgent,
    ipAddress: s.ipAddress,
    loginAt: s.loginAt,
    lastSeen: s.lastSeen,
    owner: s.email,
  }));

  const onRevoke = (session: SessionRow) => {
    // The per-user admin revoke needs the owner's userId; resolve it from the source data
    // (the rendered row carries only the email label, not the id).
    const userId = data?.find((s) => s.sessionId === session.sessionId)?.userId ?? '';
    revoke.mutate({ userId, sessionId: session.sessionId });
  };

  return (
    <Page
      breadcrumb={[{ label: t('nav.admin') }, { label: t('nav.allSessions') }]}
      title={t('nav.allSessions')}
      description={t('sessions.allDesc')}
    >

      <SessionList
        sessions={rows}
        loading={isLoading}
        hideCurrent
        showOwner
        revokingSessionId={revoke.isPending ? revoke.variables?.sessionId ?? null : null}
        onRevoke={onRevoke}
      />
    </Page>
  );
}
