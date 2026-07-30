import type { ActiveSession } from '../types';
import { useMySessions, useRevokeMySession } from '../hooks/useSessions';
import { SessionList } from '../components/SessionList';

/**
 * Self-service active-session view (K-28). Any authenticated user can see where they
 * are logged in and end an individual session. The current device (behind the
 * httpOnly refresh cookie) is flagged "This device" by the backend; revoking it signs
 * this browser out at the next access-token expiry.
 */
export function SessionsPage() {
  const { data, isLoading } = useMySessions();
  const revoke = useRevokeMySession();

  const onRevoke = (session: ActiveSession) => {
    revoke.mutate(session.sessionId);
  };

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">Sessions</h1>
        <p className="mt-1 text-sm text-muted">Devices currently signed in to your account.</p>
      </header>

      <SessionList
        sessions={data ?? []}
        loading={isLoading}
        revokingSessionId={revoke.isPending ? revoke.variables ?? null : null}
        onRevoke={onRevoke}
      />
    </div>
  );
}
