import { useState } from 'react';
import type { ActiveSession } from '../types';
import { Badge } from './ui/Badge';
import { Button } from './ui/Button';
import { ConfirmDialog } from './ui/ConfirmDialog';
import { EmptyState } from './ui/EmptyState';
import { describeUserAgent, formatDateTime, relativeTime } from '../lib/format';

interface SessionListProps {
  sessions: ActiveSession[];
  loading: boolean;
  /** Set to true on the admin view so "current" stays hidden. */
  hideCurrent?: boolean;
  revokingSessionId?: string | null;
  onRevoke: (session: ActiveSession) => void;
}

/**
 * Card list of active refresh-token sessions (K-28). Shared by the self
 * ({@code /sessions}) and admin ({@code /admin/users/:id/sessions}) pages. Revoking
 * the current device warns that the caller will be signed out here.
 */
export function SessionList({ sessions, loading, hideCurrent, revokingSessionId, onRevoke }: SessionListProps) {
  const [pending, setPending] = useState<ActiveSession | null>(null);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <span className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-muted/40 border-t-accent" />
      </div>
    );
  }

  if (sessions.length === 0) {
    return <EmptyState message="No active sessions" />;
  }

  return (
    <>
      <ul className="flex flex-col gap-3">
        {sessions.map((s) => (
          <li
            key={s.sessionId}
            className="flex flex-wrap items-center gap-3 rounded-xl border border-glass bg-surface px-5 py-4 backdrop-blur-md"
          >
            <svg className="h-5 w-5 shrink-0 text-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="2" y="3" width="20" height="14" rx="2" />
              <path d="M8 21h8" /><path d="M12 17v4" />
            </svg>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium text-main">{describeUserAgent(s.userAgent)}</span>
                {!hideCurrent && s.current && <Badge tone="accent">This device</Badge>}
              </div>
              <div className="mt-0.5 text-xs text-muted">
                {s.ipAddress ?? 'Unknown IP'} · Signed in {formatDateTime(s.loginAt)} · Active {relativeTime(s.lastSeen)} ago
              </div>
            </div>
            <Button
              size="sm"
              variant={s.current && !hideCurrent ? 'danger' : 'ghost'}
              loading={revokingSessionId === s.sessionId}
              onClick={() => setPending(s)}
            >
              Revoke
            </Button>
          </li>
        ))}
      </ul>

      <ConfirmDialog
        open={!!pending}
        title="Revoke session"
        message={
          pending?.current && !hideCurrent
            ? 'This will sign you out on this device. Continue?'
            : 'This will end the session on that device. The device may stay signed in until its access token expires.'
        }
        confirmText="Revoke"
        danger
        onConfirm={() => {
          if (pending) onRevoke(pending);
          setPending(null);
        }}
        onClose={() => setPending(null)}
      />
    </>
  );
}
