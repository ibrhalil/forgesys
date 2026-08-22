import { PERMISSIONS } from '../../lib/permissions';
import { useEffect, useRef, useState } from 'react';
import { LuEllipsisVertical, LuPencil, LuTrash2 } from 'react-icons/lu';
import { useAuthStore } from '../../store/authStore';
import { useNavigate, useParams } from 'react-router-dom';
import {
  useRole, useRoles,
  useSetRolePermissions, useSetRoleParents, useDeleteRole,
} from './hooks';
import { usePermissions } from '../permissions/hooks';
import { notify } from '../../lib/notify';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { SelectInput } from '../../components/ui/SelectInput';
import { RowMenu } from '../../components/ui/RowMenu';
import { Toggle } from '../../components/ui/Toggle';
import { toOptions } from '../../lib/select';
import type { SelectOption } from '../../lib/select';
import { DetailPanel, DetailField, PermissionBadges } from '../../components/detail/DetailPanel';
import { Page } from '../../components/Page';
import { AssignSection } from '../../components/detail/AssignSection';
import type { AssignPermissionsRequest, Role } from './types';
import type { Permission } from '../permissions/types';
import { useT } from '../../lib/i18n';
import { DetailLoading, DetailNotFound } from '../../components/detail/DetailFallback';
import { EditRoleModal } from './components/EditRoleModal';

export function RoleDetailPage() {
  const { t } = useT();
  const { roleId } = useParams<{ roleId: string }>();
  const { data: role, isLoading } = useRole(roleId);
  const { data: permissions } = usePermissions();
  const { data: rolesData } = useRoles({ size: 200, sort: 'name' });
  const setPermissions = useSetRolePermissions();
  const setParents = useSetRoleParents();
  const del = useDeleteRole();
  const navigate = useNavigate();

  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.ROLE_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.ROLE_DELETE));
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);

  if (isLoading) return <DetailLoading message={t('roles.loadingRole')} />;
  if (!role) {
    return <DetailNotFound message={t('roles.notFound')} backLabel={t('roles.backToRoles')} backTo="/roles" />;
  }

  const permissionOptions = toOptions(permissions?.items ?? [], (p) => p.id, (p) => p.name);
  // Parent candidates: every role except this one (no self-inheritance).
  const parentOptions = toOptions((rolesData?.items ?? []).filter((r) => r.id !== role.id), (r) => r.id, (r) => r.name);

  return (
    <Page
      breadcrumb={[{ label: t('nav.identity') }, { label: t('nav.roles'), to: '/roles' }, { label: role.name }]}
      title={role.name}
      description={role.description ?? undefined}
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

      <DetailPanel title={t('roles.details')}>
        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <DetailField label={t('common.name')}>{role.name}</DetailField>
          <DetailField label={t('common.permissions')}>{role.allPermissions ? t('common.all') : role.permissions.length}</DetailField>
          <DetailField label={t('roles.inheritsFrom')}>{role.parents?.length ?? 0}</DetailField>
        </dl>
      </DetailPanel>

      <RolePermissionsSection
        role={role}
        options={permissionOptions}
        saving={setPermissions.isPending}
        onSave={async (data) => {
          await setPermissions.mutateAsync({ id: role.id, data });
        }}
      />

      <AssignSection
        title={t('roles.parentSection')}
        options={parentOptions}
        selectedValues={(role.parents ?? []).map((p) => p.id)}
        saving={setParents.isPending}
        placeholder={t('roles.parentPh')}
        emptySelectedHint={t('roles.parentEmpty')}
        successMessage={t('roles.parentsUpdated')}
        onSave={async (roleIds) => {
          await setParents.mutateAsync({ id: role.id, data: { roleIds } });
        }}
      />

      <DetailPanel title={t('roles.effectivePerms', { count: role.allPermissions ? (permissions?.items ?? []).length : role.permissions.length })}>
        <PermissionBadges
          permissions={
            role.allPermissions
              ? (permissions?.items ?? []).map((p) => p.name)
              : role.permissions.map((p) => p.name)
          }
        />
      </DetailPanel>

      {editing && <EditRoleModal roleId={role.id} name={role.name} description={role.description} onClose={() => setEditing(false)} />}

      <ConfirmDialog
        open={deleting}
        title={t('roles.deleteTitle')}
        message={t('roles.deleteMsg', { name: role.name })}
        confirmText={t('common.delete')}
        danger
        loading={del.isPending}
        onConfirm={async () => {
          try { await del.mutateAsync(role.id); notify.success(t('roles.deleted')); navigate('/roles'); } catch { /* global toast */ }
        }}
        onClose={() => setDeleting(false)}
      />
    </Page>
  );
}

/**
 * Permissions assignment for a role, with an "ALL" shortcut. Toggling "Tüm izinler"
 * sets the role's all_permissions flag (it then implicitly holds every permission,
 * including ones added later); otherwise the multi-select assigns explicit ids. Tracks
 * a working draft (flag + id list) that stays in sync with the upstream role until the
 * user edits, then saves via a single Save button.
 */
function RolePermissionsSection({
  role, options, saving, onSave,
}: {
  role: Role;
  options: SelectOption<string>[];
  saving: boolean;
  onSave: (data: AssignPermissionsRequest) => Promise<void>;
}) {
  const { t } = useT();
  const [allDraft, setAllDraft] = useState(role.allPermissions);
  const [permDraft, setPermDraft] = useState<string[]>(role.permissions.map((p) => p.id));
  const interacted = useRef(false);

  useEffect(() => {
    if (!interacted.current) {
      setAllDraft(role.allPermissions);
      setPermDraft(role.permissions.map((p) => p.id));
    }
  }, [role]);

  const allDirty = allDraft !== role.allPermissions;
  const permDirty =
    permDraft.length !== role.permissions.length ||
    permDraft.some((v) => !role.permissions.some((p: Permission) => p.id === v));
  const dirty = allDraft ? allDirty : allDirty || permDirty;

  const save = async () => {
    interacted.current = false;
    if (allDraft) {
      await onSave({ all: true });
    } else {
      await onSave({ permissionIds: permDraft });
    }
  };

  return (
    <DetailPanel title={t('common.permissions')}>
      <div className="mb-3">
        <Toggle
          checked={allDraft}
          onChange={(next) => {
            interacted.current = true;
            setAllDraft(next);
          }}
          label={t('roles.allToggle')}
        />
      </div>
      {allDraft ? (
        <p className="text-sm text-muted">
          {t('roles.allHint')}
        </p>
      ) : (
        <>
          <SelectInput<string>
            isMulti
            isClearable
            options={options}
            value={options.filter((o) => permDraft.includes(o.value))}
            onChange={(next) => {
              interacted.current = true;
              setPermDraft(((next as SelectOption<string>[] | null) ?? []).map((o) => o.value));
            }}
            placeholder={t('roles.grantPh')}
          />
          {permDraft.length === 0 && (
            <p className="mt-2 text-xs text-muted/70">{t('roles.noPermsGranted')}</p>
          )}
        </>
      )}
      {/* Standard action footer: bottom-right of the editing surface, default-size
          buttons (same rule as modal footers) — never in the panel header. */}
      <div className="mt-4 flex justify-end">
        <Button variant="primary" onClick={save} disabled={!dirty} loading={saving}>
          {t('common.save')}
        </Button>
      </div>
    </DetailPanel>
  );
}

