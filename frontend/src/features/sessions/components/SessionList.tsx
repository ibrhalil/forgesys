import { useState } from 'react';
import { LuMonitor } from 'react-icons/lu';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { EmptyState } from '../../../components/ui/EmptyState';
import { Spinner } from '../../../components/ui/Spinner';
import { describeUserAgent, formatDateTime, relativeTime } from '../../../lib/format';
import { useT } from '../../../lib/i18n';

/**
 * A row the list can render — the common fields of {@link ActiveSession} plus an optional
 * owner label (the admin "all sessions" view) and an optional {@code current} flag (the
 * self view). Both the self/per-user shape and the admin tenant-wide shape map onto it.
 */
export interface SessionRow {
  sessionId: string;
  userAgent: string | null;
  ipAddress: string | null;
  loginAt: string;
  lastSeen: string;
  /** True only on the self view, for the session behind the caller's refresh cookie. */
  current?: boolean;
  /** Owner email — rendered as a separate line when {@code showOwner} is set (admin view). */
  owner?: string | null;
}

interface SessionListProps {
  sessions: SessionRow[];
  loading: boolean;
  /** Set to true on the admin views so "current" stays hidden. */
  hideCurrent?: boolean;
  /** Render the owner (email) per row — the tenant-wide admin view. */
  showOwner?: boolean;
  revokingSessionId?: string | null;
  onRevoke: (session: SessionRow) => void;
}

/**
 * Card list of active refresh-token sessions (K-28). Shared by the self
 * ({@code /sessions}), per-user admin ({@code /admin/users/:id/sessions}) and
 * tenant-wide admin ({@code /all-sessions}) pages. Revoking the current device warns
 * that the caller will be signed out here.
 */
export function SessionList({ sessions, loading, hideCurrent, showOwner, revokingSessionId, onRevoke }: SessionListProps) {
  const { t } = useT();
  const [pending, setPending] = useState<SessionRow | null>(null);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Spinner className="border-muted/40 border-t-accent" />
      </div>
    );
  }

  if (sessions.length === 0) {
    return <EmptyState message={t('sessions.empty')} />;
  }

  return (
    <>
      <ul className="flex flex-col gap-3">
        {sessions.map((s) => (
          <li
            key={s.sessionId}
            className="flex flex-wrap items-center gap-3 rounded-xl border border-glass bg-surface px-5 py-4 backdrop-blur-md"
          >
            <LuMonitor className="h-5 w-5 shrink-0 text-muted" />
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium text-main">{describeUserAgent(s.userAgent) ?? t('sessions.unknownDevice')}</span>
                {!hideCurrent && s.current && <Badge tone="accent">{t('sessions.thisDevice')}</Badge>}
              </div>
              <div className="mt-0.5 text-xs text-muted">
                {s.ipAddress ?? t('sessions.unknownIp')} · {t('sessions.signedInAt', { time: formatDateTime(s.loginAt) })} · {t('sessions.activeAgo', { time: relativeTime(s.lastSeen) })}
              </div>
              {showOwner && s.owner && (
                <div className="mt-0.5 text-xs text-muted">{t('sessions.signedInAs', { owner: s.owner })}</div>
              )}
            </div>
            <Button
              size="sm"
              variant={s.current && !hideCurrent ? 'danger' : 'ghost'}
              loading={revokingSessionId === s.sessionId}
              onClick={() => setPending(s)}
            >
              {t('sessions.revoke')}
            </Button>
          </li>
        ))}
      </ul>

      <ConfirmDialog
        open={!!pending}
        title={t('sessions.revokeTitle')}
        message={
          pending?.current && !hideCurrent
            ? t('sessions.revokeCurrentMsg')
            : t('sessions.revokeMsg')
        }
        confirmText={t('sessions.revoke')}
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
