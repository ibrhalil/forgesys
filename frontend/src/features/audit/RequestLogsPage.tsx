import type { RequestLog } from './types';
import { useRequestLogs } from './hooks';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { Page } from '../../components/Page';
import { SearchInput } from '../../components/ui/SearchInput';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { Badge } from '../../components/ui/Badge';
import { formatDateTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import { useListPageState } from '../../lib/useListPageState';

/**
 * Read-only admin view over {@code t_request_logs} (K-19 layer 3 + K-27).
 * Requires {@code iam:audit:read} — the backend enforces it; the route is guarded by
 * RequirePermission and the sidebar shows the entry only with that authority.
 * The server-side {@code q} search matches the traceId, path, and username.
 */
export function RequestLogsPage() {
  const { t } = useT();
  const {
    page, setPage, pageSize, setPageSize, sort, toggleSort,
    search, setSearch, searchFields, setSearchFields, filters, setFilters, listParams,
  } = useListPageState({ defaultSort: { field: 'createdDate', dir: 'desc' }, storageKey: 'request-logs' });
  const { data, isLoading, isFetching } = useRequestLogs(listParams);

  // Aligned with the backend's searchable registrations (RequestLogQueryService.REQUEST_LOG_FIELDS).
  const requestSearchFields = [
    { key: 'traceId', label: t('requestLog.trace'), searchable: true },
    { key: 'path', label: t('requestLog.path'), searchable: true },
    { key: 'username', label: t('requestLog.user'), searchable: true },
    { key: 'ipAddress', label: t('requestLog.ip'), searchable: true },
    { key: 'userAgent', label: t('requestLog.userAgent'), searchable: true },
    { key: 'method', label: t('requestLog.method'), searchable: false },
    { key: 'status', label: t('requestLog.status'), searchable: false },
    { key: 'durationMs', label: t('requestLog.duration'), searchable: false },
    { key: 'createdDate', label: t('requestLog.date'), searchable: false },
  ];

  const columns: Column<RequestLog>[] = [
    {
      key: 'createdAt',
      header: t('requestLog.date'),
      sortKey: 'createdDate',
      filter: { field: 'createdDate', control: 'date' },
      hideable: false,
      render: (l) => <span className="whitespace-nowrap text-muted">{formatDateTime(l.createdAt)}</span>,
    },
    {
      key: 'traceId',
      header: t('requestLog.trace'),
      sortKey: 'traceId',
      filter: { field: 'traceId', control: 'text' },
      render: (l) => <span className="font-mono text-xs text-muted/70">{l.traceId ? l.traceId.slice(0, 8) : '—'}</span>,
    },
    {
      key: 'method',
      header: t('requestLog.method'),
      sortKey: 'method',
      filter: {
        field: 'method',
        control: 'select',
        options: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map((m) => ({ value: m, label: m })),
      },
      render: (l) => l.method ? (
        <Badge tone={methodTone(l.method)}>{l.method}</Badge>
      ) : (
        <span className="text-muted">—</span>
      ),
    },
    {
      key: 'path',
      header: t('requestLog.path'),
      sortKey: 'path',
      filter: { field: 'path', control: 'text' },
      render: (l) => <span className="truncate max-w-xs font-mono text-sm">{l.path ?? '—'}</span>,
    },
    {
      key: 'status',
      header: t('requestLog.status'),
      sortKey: 'status',
      filter: { field: 'status', control: 'number' },
      render: (l) => l.status ? (
        <Badge tone={statusTone(l.status)}>{l.status}</Badge>
      ) : (
        <span className="text-muted">—</span>
      ),
    },
    {
      key: 'duration',
      header: t('requestLog.duration'),
      sortKey: 'durationMs',
      filter: { field: 'durationMs', control: 'number' },
      render: (l) => l.durationMs != null ? (
        <span className="whitespace-nowrap text-muted">{l.durationMs} ms</span>
      ) : (
        <span className="text-muted">—</span>
      ),
    },
    {
      key: 'user',
      header: t('requestLog.user'),
      sortKey: 'username',
      filter: { field: 'username', control: 'text' },
      render: (l) => (
        <div className="flex flex-col">
          <span className="text-main">{l.username ?? '—'}</span>
          {l.userId && <span className="text-xs text-muted">{l.userId}</span>}
        </div>
      ),
    },
    {
      key: 'ipAddress',
      header: t('requestLog.ip'),
      sortKey: 'ipAddress',
      filter: { field: 'ipAddress', control: 'text' },
      render: (l) => <span className="whitespace-nowrap text-muted">{l.ipAddress ?? '—'}</span>,
    },
    {
      key: 'userAgent',
      header: t('requestLog.userAgent'),
      sortKey: 'userAgent',
      filter: { field: 'userAgent', control: 'text' },
      render: (l) => <span className="truncate max-w-xs text-xs text-muted/70">{l.userAgent ?? '—'}</span>,
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.security') }, { label: t('nav.requestLogs') }]}
      title={t('nav.requestLogs')}
      description={t('requestLog.desc')}
    >
      <DataTable<RequestLog>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(l) => l.id}
        storageKey="request-logs"
        loading={isLoading || (isFetching && !data)}
        emptyMessage={search ? t('requestLog.emptyFiltered') : t('requestLog.empty')}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={setPageSize}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        sort={sort}
        onSortChange={toggleSort}
        filters={filters}
        onFiltersChange={setFilters}
        toolbar={
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder={t('requestLog.searchPh')}
            fields={requestSearchFields}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
          />
        }
      />
    </Page>
  );
}

function methodTone(method: string): 'accent' | 'blue' | 'green' | 'danger' | 'warning' | 'muted' {
  switch (method) {
    case 'GET': return 'blue';
    case 'POST': return 'accent';
    case 'PUT': case 'PATCH': return 'warning';
    case 'DELETE': return 'danger';
    default: return 'muted';
  }
}

function statusTone(status: number): 'accent' | 'blue' | 'green' | 'danger' | 'warning' | 'muted' {
  if (status >= 500) return 'danger';
  if (status >= 400) return 'warning';
  if (status >= 300) return 'muted';
  if (status >= 200) return 'green';
  return 'muted';
}