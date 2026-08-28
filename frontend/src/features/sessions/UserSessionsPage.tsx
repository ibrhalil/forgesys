import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { LuEllipsisVertical, LuMonitor, LuTrash2 } from 'react-icons/lu';
import { useUserSessions, useRevokeUserSession, useRevokeAllUserSessions } from './hooks';
import { useUser } from '../users/hooks';
import type { ActiveSession } from './types';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { RowMenu } from '../../components/ui/RowMenu';
import { Button } from '../../components/ui/Button';
import { Page } from '../../components/Page';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { SearchInput } from '../../components/ui/SearchInput';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { useClientPagination } from '../../lib/useClientPagination';
import { describeUserAgent, formatDateTime, relativeTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import type { SortState } from '../../types';

/**
 * Admin active-session view (K-28). A holder of {@code iam:user:write} can see another
 * user's active devices and end individual sessions or all of them (remote revoke).
 * Reaching this page from the Users table row action implies the caller holds the
 * permission; the backend enforces it regardless.
 */
export function UserSessionsPage() {
  const { t } = useT();
  const { userId } = useParams<{ userId: string }>();
  const { data, isLoading, isFetching, error, refetch } = useUserSessions(userId);
  // User record only feeds the breadcrumb (email segment) — skips gracefully if the
  // query is not allowed/fetched.
  const { data: user } = useUser(userId);
  const revoke = useRevokeUserSession();
  const revokeAll = useRevokeAllUserSessions();
  const [pending, setPending] = useState<ActiveSession | null>(null);
  const [revokingAll, setRevokingAll] = useState(false);
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

  const pagination = useClientPagination(filteredSessions, 10, 'user-sessions');

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
      breadcrumb={[
        { label: t('nav.identity') },
        { label: t('nav.users'), to: '/users' },
        ...(user ? [{ label: user.email, to: `/users/${userId}` }] : []),
        { label: t('sessions.userTitle') },
      ]}
      title={t('sessions.userTitle')}
      description={t('sessions.userDesc')}
      actions={(data?.length ?? 0) > 0 ? (
        /* Destructive overflow pattern: revoke-all lives only inside the menu. */
        <RowMenu
          ariaLabel={t('common.actions')}
          icon={LuEllipsisVertical}
          items={[{ label: t('sessions.revokeAll'), onClick: () => setRevokingAll(true), icon: LuTrash2, danger: true }]}
        />
      ) : undefined}
    >
      <DataTable<ActiveSession>
        columns={columns}
        data={pagination.paged}
        rowKey={(s) => s.sessionId}
        storageKey="user-sessions"
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
          if (!pending || !userId) return;
          try {
            await revoke.mutateAsync({ userId, sessionId: pending.sessionId });
            setPending(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setPending(null)}
      />

      <ConfirmDialog
        open={revokingAll}
        title={t('sessions.revokeAllTitle')}
        message={t('sessions.revokeAllMsg')}
        confirmText={t('sessions.revokeAll')}
        danger
        loading={revokeAll.isPending}
        onConfirm={async () => {
          if (!userId) return;
          await revokeAll.mutateAsync(userId);
          setRevokingAll(false);
        }}
        onClose={() => setRevokingAll(false)}
      />
    </Page>
  );
}
