import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { LuMonitor } from 'react-icons/lu';
import { useAllSessions, useRevokeUserSession } from './hooks';
import type { AdminSession } from './types';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { Page } from '../../components/Page';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { SearchInput } from '../../components/ui/SearchInput';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { useClientPagination } from '../../lib/useClientPagination';
import { describeUserAgent, formatDateTime, relativeTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import type { SortState } from '../../types';

/**
 * Tenant-wide "all sessions" admin view (iam:user:write). Lists every active
 * refresh-token session across all users of the tenant — each row shows its owner
 * (email) so the admin can see who is signed in where. Revoking a row ends that user's
 * session via the per-user admin endpoint (the device is signed out on its next
 * request). Distinct from the self {@code /sessions} page.
 */
export function AllSessionsPage() {
  const { t } = useT();
  const { data, isLoading, isFetching, error, refetch } = useAllSessions();
  const revoke = useRevokeUserSession();
  const [pending, setPending] = useState<AdminSession | null>(null);
  const [search, setSearch] = useState('');
  const [searchFields, setSearchFields] = useState<string[]>([]);
  const [sort, setSort] = useState<SortState>({ field: 'lastSeen', direction: 'desc' });

  const sessionSearchFields = [
    { key: 'email', label: t('common.user'), searchable: true },
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
      const activeKeys = searchFields.length > 0 ? searchFields : ['email', 'userAgent', 'ipAddress'];
      list = list.filter((s) => {
        return activeKeys.some((k) => {
          if (k === 'email') {
            return s.email?.toLowerCase().includes(q) ?? false;
          }
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
      if (sort.field === 'email') {
        aVal = a.email ?? '';
        bVal = b.email ?? '';
      } else if (sort.field === 'userAgent') {
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

  const pagination = useClientPagination(filteredSessions, 10, 'all-sessions');

  const columns: Column<AdminSession>[] = [
    {
      key: 'email',
      header: t('common.user'),
      sortKey: 'email',
      hideable: false,
      render: (s) => (
        <Link
          to={`/users/${s.userId}`}
          className="font-medium text-main transition-colors hover:text-accent"
        >
          {s.email}
        </Link>
      ),
    },
    {
      key: 'userAgent',
      header: t('sessions.device'),
      sortKey: 'userAgent',
      render: (s) => (
        <div className="flex items-center gap-2">
          <LuMonitor className="h-4 w-4 shrink-0 text-muted" />
          <span className="text-main">
            {describeUserAgent(s.userAgent) ?? t('sessions.unknownDevice')}
          </span>
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
      breadcrumb={[{ label: t('nav.admin') }, { label: t('nav.allSessions') }]}
      title={t('nav.allSessions')}
      description={t('sessions.allDesc')}
    >
      <DataTable<AdminSession>
        columns={columns}
        data={pagination.paged}
        rowKey={(s) => s.sessionId}
        storageKey="all-sessions"
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
            placeholder={t('sessions.allSearchPh')}
            fields={sessionSearchFields}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
          />
        }
        actionsHeader={t('common.actions')}
        actions={(s) => (
          <Button
            size="sm"
            variant="ghost"
            className="text-danger hover:bg-danger/10 hover:text-danger"
            loading={revoke.isPending && revoke.variables?.sessionId === s.sessionId}
            onClick={() => setPending(s)}
          >
            {t('sessions.revoke')}
          </Button>
        )}
      />

      <ConfirmDialog
        open={!!pending}
        title={t('sessions.revokeTitle')}
        message={t('sessions.revokeMsg')}
        confirmText={t('sessions.revoke')}
        danger
        loading={revoke.isPending}
        onConfirm={async () => {
          if (!pending) return;
          try {
            await revoke.mutateAsync({ userId: pending.userId, sessionId: pending.sessionId });
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
