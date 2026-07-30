import { useEffect, useState } from 'react';
import type { AuditLog } from './types';
import { useAuditLogs } from './hooks';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { Page } from '../../components/Page';
import { SearchInput } from '../../components/ui/SearchInput';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { Badge } from '../../components/ui/Badge';
import { formatDateTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import type { SortState } from '../../types';
import { useDebouncedValue } from '../../lib/useDebouncedValue';

const DEFAULT_PAGE_SIZE = 10;

/**
 * Read-only admin view over {@code t_audit_logs} (K-19). Requires
 * {@code iam:audit:read} — the backend enforces it; the route is guarded by
 * RequirePermission and the sidebar shows the entry only with that authority. The
 * server-side {@code q} search matches the action key and the actor/entity names.
 */
export function AuditLogsPage() {
  const { t } = useT();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState<SortState>({ field: 'createdDate', dir: 'desc' });
  const q = useDebouncedValue(search, 300);
  const { data, isLoading, isFetching } = useAuditLogs({ page, size: pageSize, sort: `${sort.field},${sort.dir}`, q: q || undefined });

  const handleSort = (field: string) => {
    setSort((prev) =>
      prev.field === field
        ? { field, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { field, dir: 'asc' },
    );
    setPage(0);
  };

  useEffect(() => {
    setPage(0);
  }, [q]);

  const columns: Column<AuditLog>[] = [
    { key: 'createdAt', header: t('audit.date'), sortKey: 'createdDate', render: (l) => <span className="whitespace-nowrap text-muted">{formatDateTime(l.createdAt)}</span> },
    { key: 'actorName', header: t('audit.actor'), sortKey: 'actorName', render: (l) => <span className="font-medium text-main">{l.actorName}</span> },
    { key: 'action', header: t('audit.action'), sortKey: 'action', render: (l) => <Badge tone="accent">{l.action}</Badge> },
    {
      key: 'entity',
      header: t('audit.target'),
      sortKey: 'entityName',
      render: (l) => (
        <div className="flex flex-col">
          <span className="text-main">{l.entityName ?? l.entityType}</span>
          <span className="text-xs text-muted">{l.entityType}</span>
        </div>
      ),
    },
    { key: 'ipAddress', header: t('audit.ip'), render: (l) => <span className="whitespace-nowrap text-muted">{l.ipAddress ?? '—'}</span> },
    { key: 'traceId', header: t('audit.trace'), render: (l) => <span className="font-mono text-xs text-muted/70">{l.traceId ? l.traceId.slice(0, 8) : '—'}</span> },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.security') }, { label: t('nav.auditLogs') }]}
      title={t('nav.auditLogs')}
      description={t('audit.desc')}
    >

      <DataTable<AuditLog>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(l) => l.id}
        loading={isLoading || (isFetching && !data)}
        emptyMessage={search ? t('audit.emptyFiltered') : t('audit.empty')}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        sort={sort}
        onSortChange={handleSort}
        toolbar={<SearchInput value={search} onChange={setSearch} placeholder={t('audit.searchPh')} />}
      />
    </Page>
  );
}
