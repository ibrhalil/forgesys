import { useEffect, useState } from 'react';
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
import type { SortState } from '../../types';
import { useDebouncedValue } from '../../lib/useDebouncedValue';

const DEFAULT_PAGE_SIZE = 10;

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
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [success, setSuccess] = useState<'all' | 'true' | 'false'>('all');
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState<SortState>({ field: 'createdDate', dir: 'desc' });
  const q = useDebouncedValue(search, 300);

  const successParam = success === 'all' ? undefined : success === 'true';
  const { data, isLoading, isFetching } = useLoginHistory({ page, size: pageSize, sort: `${sort.field},${sort.dir}`, success: successParam, q: q || undefined });

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

  const columns: Column<LoginHistory>[] = [
    { key: 'createdAt', header: t('audit.date'), sortKey: 'createdDate', render: (l) => <span className="whitespace-nowrap text-muted">{formatDateTime(l.createdAt)}</span> },
    { key: 'username', header: t('common.email'), sortKey: 'username', render: (l) => <span className="font-medium text-main">{l.username}</span> },
    {
      key: 'success',
      header: t('loginHistory.result'),
      sortKey: 'success',
      render: (l) =>
        l.success ? <Badge tone="green">{t('loginHistory.success')}</Badge> : <Badge tone="danger">{t('loginHistory.failed')}</Badge>,
    },
    { key: 'reason', header: t('loginHistory.reason'), render: (l) => <span className="text-muted">{l.reason ?? '—'}</span> },
    { key: 'ipAddress', header: t('audit.ip'), render: (l) => <span className="whitespace-nowrap text-muted">{l.ipAddress ?? '—'}</span> },
    { key: 'userAgent', header: t('loginHistory.userAgent'), render: (l) => <span className="line-clamp-1 max-w-xs text-xs text-muted/70">{l.userAgent ?? '—'}</span> },
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
        loading={isLoading || (isFetching && !data)}
        emptyMessage={search ? t('loginHistory.emptyFiltered') : t('loginHistory.empty')}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        sort={sort}
        onSortChange={handleSort}
        toolbar={(
          <>
            <SearchInput value={search} onChange={setSearch} placeholder={t('loginHistory.searchPh')} />
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
