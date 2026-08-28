import { useState } from 'react';
import type { LoginHistory } from './types';
import { useLoginHistory } from './hooks';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { Page } from '../../components/Page';
import { SearchInput } from '../../components/ui/SearchInput';
import { SelectInput } from '../../components/ui/SelectInput';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { Badge } from '../../components/ui/Badge';
import type { SelectOption } from '../../lib/select';
import { formatDateTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import { useListPageState } from '../../lib/useListPageState';

/**
 * Read-only admin view over {@code t_login_history} (K-19). Every authentication
 * attempt (successful or failed) is recorded; failures carry the stable
 * {@code ErrorCode} wire value as {@code reason}. Requires {@code iam:audit:read}.
 * The server-side {@code q} search matches the username.
 */
export function LoginHistoryPage() {
  const { t } = useT();
  const resultOptions: SelectOption<'all' | 'true' | 'false'>[] = [
    { value: 'all', label: t('common.all') },
    { value: 'true', label: t('loginHistory.success') },
    { value: 'false', label: t('loginHistory.failed') },
  ];
  const [success, setSuccess] = useState<'all' | 'true' | 'false'>('all');
  const {
    page, setPage, pageSize, setPageSize, sort, toggleSort,
    search, setSearch, searchFields, setSearchFields, filters, setFilters, listParams,
  } = useListPageState({ defaultSort: { field: 'createdDate', dir: 'desc' }, storageKey: 'login-history', syncUrl: true });

  const successParam = success === 'all' ? undefined : success === 'true';
  const { data, isLoading, isFetching, error, refetch } = useLoginHistory({
    ...listParams,
    success: successParam,
  });

  // Aligned with the backend's searchable registrations (AuditQueryService.LOGIN_HISTORY_FIELDS).
  const loginSearchFields = [
    { key: 'username', label: t('common.email'), searchable: true },
    { key: 'reason', label: t('loginHistory.reason'), searchable: true },
    { key: 'ipAddress', label: t('audit.ip'), searchable: true },
    { key: 'userAgent', label: t('loginHistory.userAgent'), searchable: true },
    { key: 'success', label: t('loginHistory.result'), searchable: false },
    { key: 'createdDate', label: t('audit.date'), searchable: false },
  ];

  const columns: Column<LoginHistory>[] = [
    {
      key: 'createdAt',
      header: t('audit.date'),
      sortKey: 'createdDate',
      filter: { field: 'createdDate', control: 'date' },
      hideable: false,
      render: (l) => <span className="whitespace-nowrap text-muted">{formatDateTime(l.createdAt)}</span>,
    },
    {
      key: 'username',
      header: t('common.email'),
      sortKey: 'username',
      filter: { field: 'username', control: 'text' },
      render: (l) => <span className="font-medium text-main">{l.username}</span>,
    },
    {
      key: 'success',
      header: t('loginHistory.result'),
      sortKey: 'success',
      filter: { field: 'success', control: 'boolean' },
      render: (l) =>
        l.success ? <Badge tone="green">{t('loginHistory.success')}</Badge> : <Badge tone="danger">{t('loginHistory.failed')}</Badge>,
    },
    {
      key: 'reason',
      header: t('loginHistory.reason'),
      sortKey: 'reason',
      filter: { field: 'reason', control: 'text' },
      render: (l) => <span className="text-muted">{l.reason ?? '—'}</span>,
    },
    {
      key: 'ipAddress',
      header: t('audit.ip'),
      sortKey: 'ipAddress',
      filter: { field: 'ipAddress', control: 'text' },
      render: (l) => <span className="whitespace-nowrap text-muted">{l.ipAddress ?? '—'}</span>,
    },
    {
      key: 'userAgent',
      header: t('loginHistory.userAgent'),
      sortKey: 'userAgent',
      filter: { field: 'userAgent', control: 'text' },
      render: (l) => <span className="line-clamp-1 max-w-xs text-xs text-muted/70">{l.userAgent ?? '—'}</span>,
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.security') }, { label: t('nav.loginHistory') }]}
      title={t('nav.loginHistory')}
      description={t('loginHistory.desc')}
    >

      <DataTable<LoginHistory>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(l) => l.id}
        storageKey="login-history"
        loading={isLoading}
        fetching={isFetching && !isLoading}
        error={error && !data ? error : undefined}
        onRetry={() => refetch()}
        emptyMessage={search ? t('loginHistory.emptyFiltered') : t('loginHistory.empty')}
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
        toolbar={(
          <>
            <SearchInput
              value={search}
              onChange={setSearch}
              placeholder={t('loginHistory.searchPh')}
              fields={loginSearchFields}
              selectedFields={searchFields}
              onSelectedFieldsChange={setSearchFields}
            />
            <SelectInput
              size="sm"
              className="w-36"
              options={resultOptions}
              value={resultOptions.find((o) => o.value === success) ?? null}
              onChange={(next) => {
                const v = (next as SelectOption<'all' | 'true' | 'false'> | null)?.value ?? 'all';
                setSuccess(v);
                setPage(0);
              }}
            />
          </>
        )}
      />
    </Page>
  );
}
