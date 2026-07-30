import { useState } from 'react';
import type { LoginHistory } from '../types';
import { useLoginHistory } from '../hooks/useAudit';
import { DataTable, type Column } from '../components/ui/DataTable';
import { Badge } from '../components/ui/Badge';
import { SelectField } from '../components/ui/Select';
import { formatDateTime } from '../lib/format';

const PAGE_SIZE = 15;

/**
 * Read-only admin view over {@code t_login_history} (K-19). Every authentication
 * attempt (successful or failed) is recorded; failures carry the stable
 * {@code ErrorCode} wire value as {@code reason}. Requires {@code iam:audit:read}.
 */
export function LoginHistoryPage() {
  const [page, setPage] = useState(0);
  const [success, setSuccess] = useState<'all' | 'true' | 'false'>('all');

  const successParam = success === 'all' ? undefined : success === 'true';
  const { data, isLoading, isFetching } = useLoginHistory({ page, size: PAGE_SIZE, sort: 'createdDate,desc', success: successParam });

  const columns: Column<LoginHistory>[] = [
    { key: 'createdAt', header: 'Date', render: (l) => <span className="whitespace-nowrap text-muted">{formatDateTime(l.createdAt)}</span> },
    { key: 'username', header: 'Email', render: (l) => <span className="font-medium text-main">{l.username}</span> },
    {
      key: 'success',
      header: 'Result',
      render: (l) =>
        l.success ? <Badge tone="green">Success</Badge> : <Badge tone="danger">Failed</Badge>,
    },
    { key: 'reason', header: 'Reason', render: (l) => <span className="text-muted">{l.reason ?? '—'}</span> },
    { key: 'ipAddress', header: 'IP', render: (l) => <span className="whitespace-nowrap text-muted">{l.ipAddress ?? '—'}</span> },
    { key: 'userAgent', header: 'User agent', render: (l) => <span className="line-clamp-1 max-w-xs text-xs text-muted/70">{l.userAgent ?? '—'}</span> },
  ];

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">Login History</h1>
        <p className="mt-1 text-sm text-muted">Every sign-in attempt, successful or failed.</p>
      </header>

      <div className="w-48">
        <SelectField
          label="Result"
          value={success}
          onChange={(e) => { setSuccess(e.target.value as typeof success); setPage(0); }}
        >
          <option value="all">All</option>
          <option value="true">Successful</option>
          <option value="false">Failed</option>
        </SelectField>
      </div>

      <DataTable<LoginHistory>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(l) => l.id}
        loading={isLoading || (isFetching && !data)}
        emptyMessage="No login attempts"
        page={data?.page ?? page}
        pageSize={data?.size ?? PAGE_SIZE}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
      />
    </div>
  );
}
