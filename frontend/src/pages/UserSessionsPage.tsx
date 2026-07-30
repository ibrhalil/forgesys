import { useParams } from 'react-router-dom';
import type { ActiveSession } from '../types';
import { useUserSessions, useRevokeUserSession, useRevokeAllUserSessions } from '../hooks/useSessions';
import { SessionList } from '../components/SessionList';
import { Button } from '../components/ui/Button';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { useState } from 'react';

/**
 * Admin active-session view (K-28). A holder of {@code iam:user:write} can see another
 * user's active devices and end individual sessions or all of them (remote revoke).
 * Reaching this page from the Users table row action implies the caller holds the
 * permission; the backend enforces it regardless.
 */
export function UserSessionsPage() {
  const { userId } = useParams<{ userId: string }>();
  const { data, isLoading } = useUserSessions(userId);
  const revoke = useRevokeUserSession();
  const revokeAll = useRevokeAllUserSessions();
  const [revokingAll, setRevokingAll] = useState(false);

  const onRevoke = (session: ActiveSession) => {
    if (!userId) return;
    revoke.mutate({ userId, sessionId: session.sessionId });
  };

  return (
    <div className="flex flex-col gap-6">
      <header className="flex items-center justify-between gap-4">
        <div>
          <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">User Sessions</h1>
          <p className="mt-1 text-sm text-muted">Active devices for this user. Ending a session drops its refresh token.</p>
        </div>
        {(data?.length ?? 0) > 0 && (
          <Button variant="danger" onClick={() => setRevokingAll(true)}>Revoke all</Button>
        )}
      </header>

      <SessionList
        sessions={data ?? []}
        loading={isLoading}
        hideCurrent
        revokingSessionId={revoke.isPending ? revoke.variables?.sessionId ?? null : null}
        onRevoke={onRevoke}
      />

      <ConfirmDialog
        open={revokingAll}
        title="Revoke all sessions"
        message="End every active session for this user? They'll need to sign in again on every device."
        confirmText="Revoke all"
        danger
        loading={revokeAll.isPending}
        onConfirm={async () => {
          if (!userId) return;
          await revokeAll.mutateAsync(userId);
          setRevokingAll(false);
        }}
        onClose={() => setRevokingAll(false)}
      />
    </div>
  );
}
