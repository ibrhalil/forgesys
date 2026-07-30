import { useState } from 'react';
import type { AuditLog } from '../types';
import { useAuditLogs } from '../hooks/useAudit';
import { DataTable, type Column } from '../components/ui/DataTable';
import { Badge } from '../components/ui/Badge';
import { TextField } from '../components/ui/Field';
import { Button } from '../components/ui/Button';
import { formatDateTime } from '../lib/format';

const PAGE_SIZE = 15;

/**
 * Read-only admin view over {@code t_audit_logs} (K-19). Requires
 * {@code iam:audit:read} — the backend enforces it; this page is reachable from the
 * sidebar only when the user holds that authority (PermissionGate). An optional
 * action-key filter narrows the feed (e.g. {@code user_created}).
 */
export function AuditLogsPage() {
  const [page, setPage] = useState(0);
  const [actionInput, setActionInput] = useState('');
  const [actionFilter, setActionFilter] = useState<string | undefined>(undefined);
  const { data, isLoading, isFetching } = useAuditLogs({ page, size: PAGE_SIZE, sort: 'createdDate,desc', action: actionFilter });

  const columns: Column<AuditLog>[] = [
    { key: 'createdAt', header: 'Date', render: (l) => <span className="whitespace-nowrap text-muted">{formatDateTime(l.createdAt)}</span> },
    { key: 'actorName', header: 'Actor', render: (l) => <span className="font-medium text-main">{l.actorName}</span> },
    { key: 'action', header: 'Action', render: (l) => <Badge tone="accent">{l.action}</Badge> },
    {
      key: 'entity',
      header: 'Target',
      render: (l) => (
        <div className="flex flex-col">
          <span className="text-main">{l.entityName ?? l.entityType}</span>
          <span className="text-xs text-muted">{l.entityType}</span>
        </div>
      ),
    },
    { key: 'ipAddress', header: 'IP', render: (l) => <span className="whitespace-nowrap text-muted">{l.ipAddress ?? '—'}</span> },
    { key: 'traceId', header: 'Trace', render: (l) => <span className="font-mono text-xs text-muted/70">{l.traceId ? l.traceId.slice(0, 8) : '—'}</span> },
  ];

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">Audit Log</h1>
        <p className="mt-1 text-sm text-muted">Administrative actions recorded across the tenant.</p>
      </header>

      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setActionFilter(actionInput.trim() || undefined);
        }}
      >
        <TextField
          label="Filter by action"
          value={actionInput}
          onChange={(e) => setActionInput(e.target.value)}
          placeholder="e.g. user_created"
          className="w-64"
        />
        <Button type="submit" variant="secondary">Apply</Button>
        {actionFilter && (
          <Button type="button" variant="ghost" onClick={() => { setActionInput(''); setActionFilter(undefined); setPage(0); }}>
            Clear
          </Button>
        )}
      </form>

      <DataTable<AuditLog>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(l) => l.id}
        loading={isLoading || (isFetching && !data)}
        emptyMessage="No audit entries"
        page={data?.page ?? page}
        pageSize={data?.size ?? PAGE_SIZE}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
      />
    </div>
  );
}
