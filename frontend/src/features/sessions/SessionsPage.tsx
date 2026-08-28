import { useMemo, useState } from 'react';
import { LuMonitor } from 'react-icons/lu';
import { useMySessions, useRevokeMySession } from './hooks';
import type { ActiveSession } from './types';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { Page } from '../../components/Page';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { SearchInput } from '../../components/ui/SearchInput';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { useClientPagination } from '../../lib/useClientPagination';
import { describeUserAgent, formatDateTime, relativeTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import type { SortState } from '../../types';

/**
 * Self-service active-session view (K-28). Any authenticated user can see where they
 * are logged in and end an individual session. The current device (behind the httpOnly
 * refresh cookie) is flagged "This device"; revoking it signs this browser out
 * immediately (the access token is invalidated on the next request).
 */
export function SessionsPage() {
  const { t } = useT();
  const { data, isLoading, isFetching, error, refetch } = useMySessions();
  const revoke = useRevokeMySession();
  const [pending, setPending] = useState<ActiveSession | null>(null);
  const [search, setSearch] = useState('');
  const [searchFields, setSearchFields] = useState<string[]>([]);
  const [sort, setSort] = useState<SortState>({ field: 'lastSeen', direction: 'desc' });

  const sessionSearchFields = [
    { key: 'userAgent', label: t('sessions.device'), searchable: true },
    { key: 'ipAddress', label: t('sessions.ipAddress'), searchable: true },
  ];

  const toggleSort = (field: string) => {
    setSort((prev) => {
      if (prev.field === field) {
        return { field, direction: prev.direction === 'asc' ? 'desc' : 'asc' };
      }
      return { field, direction: 'asc' };
    });
  };

  const filteredSessions = useMemo(() => {
    let list = data ?? [];
    if (search.trim()) {
      const q = search.toLowerCase();
      const activeKeys = searchFields.length > 0 ? searchFields : ['userAgent', 'ipAddress'];
      list = list.filter((s) => {
        return activeKeys.some((k) => {
          if (k === 'userAgent') {
            const desc = describeUserAgent(s.userAgent)?.toLowerCase() ?? '';
            const raw = s.userAgent?.toLowerCase() ?? '';
            return desc.includes(q) || raw.includes(q);
          }
          if (k === 'ipAddress') {
            return s.ipAddress?.toLowerCase().includes(q) ?? false;
          }
          return false;
        });
      });
    }
    return [...list].sort((a, b) => {
      let aVal: string | number = '';
      let bVal: string | number = '';
      if (sort.field === 'userAgent') {
        aVal = describeUserAgent(a.userAgent) ?? a.userAgent ?? '';
        bVal = describeUserAgent(b.userAgent) ?? b.userAgent ?? '';
      } else if (sort.field === 'ipAddress') {
        aVal = a.ipAddress ?? '';
        bVal = b.ipAddress ?? '';
      } else if (sort.field === 'loginAt') {
        aVal = new Date(a.loginAt).getTime() || 0;
        bVal = new Date(b.loginAt).getTime() || 0;
      } else if (sort.field === 'lastSeen') {
        aVal = new Date(a.lastSeen).getTime() || 0;
        bVal = new Date(b.lastSeen).getTime() || 0;
      }
      if (aVal < bVal) return sort.direction === 'asc' ? -1 : 1;
      if (aVal > bVal) return sort.direction === 'asc' ? 1 : -1;
      return 0;
    });
  }, [data, search, searchFields, sort]);

  const pagination = useClientPagination(filteredSessions, 10, 'sessions');

  const columns: Column<ActiveSession>[] = [
    {
      key: 'userAgent',
      header: t('sessions.device'),
      sortKey: 'userAgent',
      hideable: false,
      render: (s) => (
        <div className="flex items-center gap-2">
          <LuMonitor className="h-4 w-4 shrink-0 text-muted" />
          <span className="font-medium text-main">
            {describeUserAgent(s.userAgent) ?? t('sessions.unknownDevice')}
          </span>
          {s.current && <Badge tone="accent">{t('sessions.thisDevice')}</Badge>}
        </div>
      ),
    },
    {
      key: 'ipAddress',
      header: t('sessions.ipAddress'),
      sortKey: 'ipAddress',
      render: (s) => <span className="text-muted">{s.ipAddress ?? t('sessions.unknownIp')}</span>,
    },
    {
      key: 'loginAt',
      header: t('sessions.loginAt'),
      sortKey: 'loginAt',
      render: (s) => <span className="whitespace-nowrap text-muted">{formatDateTime(s.loginAt)}</span>,
    },
    {
      key: 'lastSeen',
      header: t('sessions.lastSeen'),
      sortKey: 'lastSeen',
      render: (s) => (
        <span className="whitespace-nowrap text-muted" title={formatDateTime(s.lastSeen)}>
          {relativeTime(s.lastSeen)}
        </span>
      ),
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.security') }, { label: t('nav.sessions') }]}
      title={t('nav.sessions')}
      description={t('sessions.desc')}
    >
      <DataTable<ActiveSession>
        columns={columns}
        data={pagination.paged}
        rowKey={(s) => s.sessionId}
        storageKey="sessions"
        loading={isLoading}
        fetching={isFetching && !isLoading}
        error={error && !data ? error : undefined}
        onRetry={() => refetch()}
        onRefresh={() => refetch()}
        emptyMessage={search ? t('sessions.emptyFiltered') : t('sessions.empty')}
        page={pagination.page}
        pageSize={pagination.pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={pagination.setPageSize}
        totalElements={pagination.totalElements}
        totalPages={pagination.totalPages}
        onPageChange={pagination.setPage}
        sort={sort}
        onSortChange={toggleSort}
        toolbar={
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder={t('sessions.searchPh')}
            fields={sessionSearchFields}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
          />
        }
        actionsHeader={t('common.actions')}
        actions={(s) => (
          <Button
            size="sm"
            variant={s.current ? 'danger' : 'ghost'}
            className={s.current ? undefined : 'text-danger hover:bg-danger/10 hover:text-danger'}
            loading={revoke.isPending && revoke.variables === s.sessionId}
            onClick={() => setPending(s)}
          >
            {t('sessions.revoke')}
          </Button>
        )}
      />

      <ConfirmDialog
        open={!!pending}
        title={t('sessions.revokeTitle')}
        message={pending?.current ? t('sessions.revokeCurrentMsg') : t('sessions.revokeMsg')}
        confirmText={t('sessions.revoke')}
        danger
        loading={revoke.isPending}
        onConfirm={async () => {
          if (!pending) return;
          try {
            await revoke.mutateAsync(pending.sessionId);
            setPending(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setPending(null)}
      />
    </Page>
  );
}
