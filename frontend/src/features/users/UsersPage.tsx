import { PERMISSIONS } from '../../lib/permissions';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { UserDirectoryView } from './types';
import { isLocked } from './types';
import { useUsers, useDeleteUser, useUnlockUser } from './hooks';
import { useAuthStore } from '../../store/authStore';
import { useT } from '../../lib/i18n';
import { useListPageState } from '../../lib/useListPageState';

import { LuEye, LuKeyRound, LuLockOpen, LuMonitor, LuShield, LuTrash2, LuUsers as LuGroup } from 'react-icons/lu';
import { notify } from '../../lib/notify';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { SearchInput } from '../../components/ui/SearchInput';
import { RowMenu } from '../../components/ui/RowMenu';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Page } from '../../components/Page';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { AssignRolesModal } from './components/AssignRolesModal';
import { AssignGroupsModal } from './components/AssignGroupsModal';
import { ResetPasswordModal } from './components/ResetPasswordModal';

export function UsersPage() {
  const { t } = useT();
  const {
    page,
    setPage,
    pageSize,
    setPageSize,
    sort,
    toggleSort,
    search,
    setSearch,
    searchFields,
    setSearchFields,
    q,
  } = useListPageState({ defaultSort: { field: 'email', dir: 'asc' }, storageKey: 'users' });
  const { data, isLoading, isFetching } = useUsers({ page, size: pageSize, sorts: [sort], q: q || undefined });
  const delUser = useDeleteUser();
  const unlockUser = useUnlockUser();
  const navigate = useNavigate();
  const currentUserId = useAuthStore((s) => s.user?.id);
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.USER_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.USER_DELETE));

  const [assignRolesTo, setAssignRolesTo] = useState<UserDirectoryView | null>(null);
  const [assignGroupsTo, setAssignGroupsTo] = useState<UserDirectoryView | null>(null);
  const [resetPwdFor, setResetPwdFor] = useState<UserDirectoryView | null>(null);
  const [unlocking, setUnlocking] = useState<UserDirectoryView | null>(null);
  const [deleting, setDeleting] = useState<UserDirectoryView | null>(null);

  const userSearchFields = [
    { key: 'name', label: t('common.user'), searchable: true },
    { key: 'status', label: t('common.status'), searchable: false },
    { key: 'roles', label: t('common.roles'), searchable: false },
    { key: 'groups', label: t('common.groups'), searchable: false },
  ];

  const columns: Column<UserDirectoryView>[] = [
    {
      key: 'name',
      header: t('common.user'),
      // Column key ≠ backend field: the composite name cell sorts by email.
      sortKey: 'email',
      hideable: false,
      render: (u) => (
        <div className="flex flex-col">
          <Link to={`/users/${u.id}`} className="font-medium text-main transition-colors hover:text-accent">{u.email}</Link>
          {(u.firstName || u.lastName) && (
            <span className="text-xs text-muted">
              {[u.firstName, u.lastName].filter(Boolean).join(' ')}
            </span>
          )}
        </div>
      ),
    },
    { key: 'username', header: t('common.username'), sortKey: 'username', render: (u) => <span className="text-muted">{u.username}</span> },
    {
      key: 'status',
      header: t('common.status'),
      render: (u) => (
        <div className="flex flex-wrap gap-1">
          <Badge tone={u.enabled ? 'green' : 'muted'}>{u.enabled ? t('common.active') : t('common.disabled')}</Badge>
          {/* Lazy lockout expiry: only a future timestamp renders as locked. */}
          {isLocked(u) && <Badge tone="warning">{t('common.locked')}</Badge>}
          {u.emailVerified && <Badge tone="blue">{t('common.verified')}</Badge>}
        </div>
      ),
    },
    {
      key: 'roles',
      header: t('common.roles'),
      // Count-only on purpose (detail page shows the full list).
      render: (u) =>
        u.roleCount ? (
          <span>
            <span className="font-semibold text-accent">{u.roleCount}</span>{' '}
            <span className="text-muted">{t('common.roles').toLowerCase()}</span>
          </span>
        ) : (
          <span className="text-muted">—</span>
        ),
    },
    {
      key: 'groups',
      header: t('common.groups'),
      render: (u) =>
        u.groupCount ? (
          <span>
            <span className="font-semibold text-accent-blue">{u.groupCount}</span>{' '}
            <span className="text-muted">{t('common.groups').toLowerCase()}</span>
          </span>
        ) : (
          <span className="text-muted">—</span>
        ),
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.identity') }, { label: t('nav.users') }]}
      title={t('users.title')}
      description={t('users.desc')}
      actions={canWrite ? <Button variant="primary" onClick={() => navigate('/users/new')}>{t('users.new')}</Button> : undefined}
    >

      <DataTable<UserDirectoryView>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(u) => u.id}
        storageKey="users"
        loading={isLoading || (isFetching && !data)}
        emptyMessage={q ? t('users.emptyFiltered') : t('users.empty')}
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
            placeholder={t('users.searchPh')}
            fields={userSearchFields}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
          />
        }
        actionsHeader={t('common.actions')}
        actions={(u) => (
          <RowMenu
            ariaLabel={t('common.actions')}
            items={[
              { label: t('common.view'), onClick: () => navigate(`/users/${u.id}`), icon: LuEye },
              ...(canWrite
                ? [
                    { label: t('common.roles'), onClick: () => setAssignRolesTo(u), icon: LuShield },
                    { label: t('common.groups'), onClick: () => setAssignGroupsTo(u), icon: LuGroup },
                    { label: t('users.passwordBtn'), onClick: () => setResetPwdFor(u), icon: LuKeyRound },
                    // Admin session view requires iam:user:write on the backend too.
                    { label: t('nav.sessions'), onClick: () => navigate(`/admin/users/${u.id}/sessions`), icon: LuMonitor },
                  ]
                : []),
              // Unlock only makes sense for an actively locked account.
              ...(canWrite && isLocked(u)
                ? [{ label: t('users.unlock'), onClick: () => setUnlocking(u), icon: LuLockOpen }]
                : []),
              /* Self-delete is rejected by the backend (409 self_delete_forbidden) — omit it on the actor's own row. */
              ...(canDelete && u.id !== currentUserId
                ? [{ label: t('common.delete'), onClick: () => setDeleting(u), icon: LuTrash2, danger: true }]
                : []),
            ]}
          />
        )}
      />

      {assignRolesTo && <AssignRolesModal user={assignRolesTo} onClose={() => setAssignRolesTo(null)} />}
      {assignGroupsTo && <AssignGroupsModal user={assignGroupsTo} onClose={() => setAssignGroupsTo(null)} />}
      {resetPwdFor && <ResetPasswordModal user={resetPwdFor} onClose={() => setResetPwdFor(null)} />}

      <ConfirmDialog
        open={!!unlocking}
        title={t('users.unlockTitle')}
        message={t('users.unlockMsg', { email: unlocking?.email ?? '' })}
        confirmText={t('users.unlock')}
        loading={unlockUser.isPending}
        onConfirm={async () => {
          if (!unlocking) return;
          try {
            await unlockUser.mutateAsync(unlocking.id);
            notify.success(t('users.unlocked'));
            setUnlocking(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setUnlocking(null)}
      />

      <ConfirmDialog
        open={!!deleting}
        title={t('users.deleteTitle')}
        message={t('users.deleteMsg', { email: deleting?.email ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delUser.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await delUser.mutateAsync(deleting.id);
            notify.success(t('users.deleted'));
            setDeleting(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeleting(null)}
      />
    </Page>
  );
}
