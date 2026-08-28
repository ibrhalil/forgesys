import type { PlatformAuditEntry } from './types';
import { usePlatformAuditLogs } from './hooks';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { Page } from '../../components/Page';
import { SearchInput } from '../../components/ui/SearchInput';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { Badge } from '../../components/ui/Badge';
import { formatDateTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import { useListPageState } from '../../lib/useListPageState';

/**
 * Platform audit trail (K-50 F7): paged view over {@code t_platform_audit_logs}
 * — platform sign-ins, tenant lifecycle, key management, switch/impersonation
 * events. Read-only; simple GET filters (no POST search endpoint in v1).
 */
export function PlatformAuditLogsPage() {
  const { t } = useT();
  const {
    page, setPage, pageSize, setPageSize, sort, toggleSort,
    search, setSearch, searchFields, setSearchFields, listParams,
  } = useListPageState({ defaultSort: { field: 'createdDate', direction: 'desc' }, storageKey: 'platform-audit', syncUrl: true });
  const { data, isLoading, isFetching, error, refetch } = usePlatformAuditLogs(listParams);

  // Aligned with the backend's searchable registrations
  // (PlatformAuditQueryService.FILTER_FIELDS): q matches action/targetType/IP/trace.
  const searchFieldOptions = [
    { key: 'action', label: t('audit.action'), searchable: true },
    { key: 'targetType', label: t('audit.target'), searchable: true },
    { key: 'ipAddress', label: t('audit.ip'), searchable: true },
    { key: 'traceId', label: t('audit.trace'), searchable: true },
  ];

  const columns: Column<PlatformAuditEntry>[] = [
    {
      key: 'createdAt',
      header: t('audit.date'),
      sortKey: 'createdDate',
      hideable: false,
      render: (e) => <span className="whitespace-nowrap text-muted">{formatDateTime(e.createdAt)}</span>,
    },
    {
      key: 'actor',
      header: t('audit.actor'),
      render: (e) => (
        <div className="flex flex-col">
          <span className="font-mono text-xs text-muted/70">{e.actorId ? e.actorId.slice(0, 8) : '—'}</span>
          <Badge tone={e.actorType === 'HUMAN' ? 'accent' : e.actorType === 'SERVICE' ? 'blue' : 'muted'}>
            {e.actorType}
          </Badge>
        </div>
      ),
    },
    { key: 'action', header: t('audit.action'), render: (e) => <Badge tone="accent">{e.action}</Badge> },
    {
      key: 'target',
      header: t('audit.target'),
      render: (e) => (
        <div className="flex flex-col">
          <span className="text-main">{e.targetType ?? '—'}</span>
          {e.targetId && <span className="font-mono text-xs text-muted/70">{e.targetId.slice(0, 8)}</span>}
        </div>
      ),
    },
    {
      key: 'detail',
      header: t('platform.audit.detail'),
      render: (e) => (
        <span className="block max-w-[280px] truncate text-sm text-muted" title={e.detail ?? undefined}>
          {e.detail ?? '—'}
        </span>
      ),
    },
    {
      key: 'ipAddress',
      header: t('audit.ip'),
      render: (e) => <span className="whitespace-nowrap text-muted">{e.ipAddress ?? '—'}</span>,
    },
    {
      key: 'traceId',
      header: t('audit.trace'),
      render: (e) => <span className="font-mono text-xs text-muted/70">{e.traceId ? e.traceId.slice(0, 8) : '—'}</span>,
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('platform.console') }, { label: t('platform.nav.auditLogs') }]}
      title={t('platform.nav.auditLogs')}
      description={t('platform.audit.desc')}
    >
      <DataTable<PlatformAuditEntry>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(e) => e.id}
        storageKey="platform-audit"
        loading={isLoading}
        fetching={isFetching && !isLoading}
        error={error && !data ? error : undefined}
        onRetry={() => refetch()}
        emptyMessage={search ? t('platform.audit.emptyFiltered') : t('platform.audit.empty')}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={setPageSize}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        sort={sort}
        onSortChange={toggleSort}
        toolbar={
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder={t('platform.audit.searchPh')}
            fields={searchFieldOptions}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
          />
        }
      />
    </Page>
  );
}
