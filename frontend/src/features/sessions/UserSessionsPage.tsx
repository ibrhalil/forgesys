import { useParams } from 'react-router-dom';
import { useUserSessions, useRevokeUserSession, useRevokeAllUserSessions } from './hooks';
import { useUser } from '../users/hooks';
import { SessionList, type SessionRow } from './components/SessionList';
import { Button } from '../../components/ui/Button';
import { Page } from '../../components/Page';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useState } from 'react';
import { useT } from '../../lib/i18n';

/**
 * Admin active-session view (K-28). A holder of {@code iam:user:write} can see another
 * user's active devices and end individual sessions or all of them (remote revoke).
 * Reaching this page from the Users table row action implies the caller holds the
 * permission; the backend enforces it regardless.
 */
export function UserSessionsPage() {
  const { t } = useT();
  const { userId } = useParams<{ userId: string }>();
  const { data, isLoading } = useUserSessions(userId);
  // User record only feeds the breadcrumb (email segment) — skips gracefully if the
  // query is not allowed/fetched.
  const { data: user } = useUser(userId);
  const revoke = useRevokeUserSession();
  const revokeAll = useRevokeAllUserSessions();
  const [revokingAll, setRevokingAll] = useState(false);

  const onRevoke = (session: SessionRow) => {
    if (!userId) return;
    revoke.mutate({ userId, sessionId: session.sessionId });
  };

  return (
    <Page
      breadcrumb={[
        { label: t('nav.identity') },
        { label: t('nav.users'), to: '/users' },
        ...(user ? [{ label: user.email, to: `/users/${userId}` }] : []),
        { label: t('sessions.userTitle') },
      ]}
      title={t('sessions.userTitle')}
      description={t('sessions.userDesc')}
      actions={(data?.length ?? 0) > 0 ? (
        <Button variant="danger" onClick={() => setRevokingAll(true)}>{t('sessions.revokeAll')}</Button>
      ) : undefined}
    >

      <SessionList
        sessions={data ?? []}
        loading={isLoading}
        hideCurrent
        revokingSessionId={revoke.isPending ? revoke.variables?.sessionId ?? null : null}
        onRevoke={onRevoke}
      />

      <ConfirmDialog
        open={revokingAll}
        title={t('sessions.revokeAllTitle')}
        message={t('sessions.revokeAllMsg')}
        confirmText={t('sessions.revokeAll')}
        danger
        loading={revokeAll.isPending}
        onConfirm={async () => {
          if (!userId) return;
          await revokeAll.mutateAsync(userId);
          setRevokingAll(false);
        }}
        onClose={() => setRevokingAll(false)}
      />
    </Page>
  );
}
