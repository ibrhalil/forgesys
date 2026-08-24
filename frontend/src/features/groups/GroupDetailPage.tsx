import { PERMISSIONS } from '../../lib/permissions';
import { useState } from 'react';
import { LuEllipsisVertical, LuPencil, LuTrash2 } from 'react-icons/lu';
import { useAuthStore } from '../../store/authStore';
import { useNavigate, useParams } from 'react-router-dom';
import {
  useGroup, useGroupEffectivePermissions,
  useSetGroupRoles, useSetGroupMembers, useDeleteGroup,
} from './hooks';
import { rolesApi } from '../roles/api';
import { usersApi } from '../users/api';
import { notify } from '../../lib/notify';

import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { RowMenu } from '../../components/ui/RowMenu';
import { DetailPanel, DetailField, PermissionBadges } from '../../components/detail/DetailPanel';
import { Page } from '../../components/Page';
import { AssignSection } from '../../components/detail/AssignSection';
import { useT } from '../../lib/i18n';
import { DetailLoading, DetailNotFound } from '../../components/detail/DetailFallback';
import { EditGroupModal } from './components/EditGroupModal';

export function GroupDetailPage() {
  const { t } = useT();
  const { groupId } = useParams<{ groupId: string }>();
  const { data: group, isLoading } = useGroup(groupId);
  const { data: effectivePerms } = useGroupEffectivePermissions(groupId);
  const setRoles = useSetGroupRoles();
  const setMembers = useSetGroupMembers();
  const del = useDeleteGroup();
  const navigate = useNavigate();

  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.GROUP_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.GROUP_DELETE));
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);

  if (isLoading) return <DetailLoading message={t('groups.loadingGroup')} />;
  if (!group) {
    return <DetailNotFound message={t('groups.notFound')} backLabel={t('groups.backToGroups')} backTo="/groups" />;
  }

  return (
    <Page
      breadcrumb={[{ label: t('nav.identity') }, { label: t('nav.groups'), to: '/groups' }, { label: group.name }]}
      title={(
        <span className="flex flex-wrap items-center gap-3">
          <span className="truncate">{group.name}</span>
          <Badge tone={group.active ? 'green' : 'muted'}>{group.active ? t('common.active') : t('common.inactive')}</Badge>
        </span>
      )}
      description={group.description ?? undefined}
      actions={(
        <>
          {/* Head pattern: at most one visible action + overflow menu (RowMenu).
              Empty items (no authority) render no trigger. */}
          {canWrite && <Button size="sm" variant="ghost" onClick={() => setEditing(true)}>
            <LuPencil className="h-3.5 w-3.5" />
            {t('common.edit')}
          </Button>}
          <RowMenu
            ariaLabel={t('common.actions')}
            icon={LuEllipsisVertical}
            items={
              canDelete
                ? [{ label: t('common.delete'), onClick: () => setDeleting(true), icon: LuTrash2, danger: true }]
                : []
            }
          />
        </>
      )}
    >

      <DetailPanel title={t('common.details')}>
        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <DetailField label={t('common.name')}>{group.name}</DetailField>
          <DetailField label={t('common.status')}>{group.active ? t('common.active') : t('common.inactive')}</DetailField>
          <DetailField label={t('common.members')}>{group.memberCount}</DetailField>
          <DetailField label={t('common.roles')}>{group.roles.length}</DetailField>
        </dl>
      </DetailPanel>

      {/* Editing surfaces only for iam:group:write holders — a read-only viewer
          sees the current assignments as badges (UserDetailPage pattern). */}
      <div className="grid gap-6 lg:grid-cols-2">
        {canWrite ? (
          <AssignSection
            title={t('common.roles')}
            loadOptions={(input) =>
              rolesApi
                .list({ q: input, size: 20, sorts: [{ field: 'name', dir: 'asc' }] })
                .then((page) => page.items.map((r) => ({ value: r.id, label: r.name })))
                .catch(() => [])
            }
            selectedOptions={group.roles.map((r) => ({ value: r.id, label: r.name }))}
            selectedValues={group.roles.map((r) => r.id)}
            saving={setRoles.isPending}
            placeholder={t('groups.rolesPh')}
            emptySelectedHint={t('groups.rolesEmpty')}
            successMessage={t('common.rolesUpdated')}
            onSave={async (roleIds) => {
              await setRoles.mutateAsync({ id: group.id, data: { roleIds } });
            }}
          />
        ) : (
          <DetailPanel title={t('common.roles')}>
            {group.roles.length === 0 ? (
              <p className="text-sm text-muted">{t('groups.rolesEmpty')}</p>
            ) : (
              <div className="flex flex-wrap gap-1.5">
                {group.roles.map((r) => (
                  <Badge key={r.id} tone="accent">{r.name}</Badge>
                ))}
              </div>
            )}
          </DetailPanel>
        )}

        {canWrite ? (
          <AssignSection
            title={t('groups.membersSection', { count: group.memberCount })}
            loadOptions={(input) =>
              usersApi
                .list({ q: input, size: 20, sorts: [{ field: 'email', dir: 'asc' }] })
                .then((page) => page.items.map((u) => ({ value: u.id, label: u.email })))
                .catch(() => [])
            }
            selectedOptions={group.members.map((m) => ({ value: m.id, label: m.email }))}
            selectedValues={group.members.map((m) => m.id)}
            saving={setMembers.isPending}
            placeholder={t('groups.membersPh')}
            emptySelectedHint={t('groups.membersEmpty')}
            successMessage={t('groups.membersUpdated')}
            onSave={async (userIds) => {
              await setMembers.mutateAsync({ id: group.id, data: { userIds } });
            }}
          />
        ) : (
          <DetailPanel title={t('groups.membersSection', { count: group.memberCount })}>
            {group.members.length === 0 ? (
              <p className="text-sm text-muted">{t('groups.membersEmpty')}</p>
            ) : (
              <div className="flex flex-wrap gap-1.5">
                {group.members.map((m) => (
                  <Badge key={m.id} tone="blue">{m.email}</Badge>
                ))}
              </div>
            )}
          </DetailPanel>
        )}
      </div>

      <DetailPanel title={t('groups.effectivePerms', { count: effectivePerms?.length ?? 0 })}>
        {!effectivePerms ? (
          <p className="text-sm text-muted">{t('common.loading')}</p>
        ) : (
          <PermissionBadges permissions={effectivePerms} />
        )}
      </DetailPanel>

      {editing && <EditGroupModal groupId={group.id} name={group.name} description={group.description} active={group.active} onClose={() => setEditing(false)} />}

      <ConfirmDialog
        open={deleting}
        title={t('groups.deleteTitle')}
        message={t('groups.deleteMsgDetail', { name: group.name })}
        confirmText={t('common.delete')}
        danger
        loading={del.isPending}
        onConfirm={async () => {
          try { await del.mutateAsync(group.id); notify.success(t('groups.deleted')); navigate('/groups'); } catch { /* global toast */ }
        }}
        onClose={() => setDeleting(false)}
      />
    </Page>
  );
}

