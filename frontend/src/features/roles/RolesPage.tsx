import { PERMISSIONS } from '../../lib/permissions';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Role } from './types';
import { useRoles, useCreateRole, useDeleteRole, useSetRolePermissions } from './hooks';
import { usePermissions } from '../permissions/hooks';
import { notify, extractFieldErrors } from '../../lib/notify';
import { LuShieldCheck, LuTrash2 } from 'react-icons/lu';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { SearchInput } from '../../components/ui/SearchInput';
import { RowMenu } from '../../components/ui/RowMenu';
import { Modal } from '../../components/ui/Modal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Toggle } from '../../components/ui/Toggle';
import { Button } from '../../components/ui/Button';
import { Page } from '../../components/Page';
import { Badge } from '../../components/ui/Badge';
import { TextField } from '../../components/ui/Field';
import { TextAreaField } from '../../components/ui/TextArea';
import { CheckboxList, type CheckboxItem } from '../../components/ui/CheckboxList';
import { useT } from '../../lib/i18n';
import { useListPageState } from '../../lib/useListPageState';
import { useAuthStore } from '../../store/authStore';

export function RolesPage() {
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
  } = useListPageState({ defaultSort: { field: 'name', dir: 'asc' }, storageKey: 'roles' });
  const { data, isLoading, isFetching } = useRoles({ page, size: pageSize, sorts: [sort], q: q || undefined });
  const delRole = useDeleteRole();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.ROLE_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.ROLE_DELETE));

  const [creating, setCreating] = useState(false);
  const [assignPermsTo, setAssignPermsTo] = useState<Role | null>(null);
  const [deleting, setDeleting] = useState<Role | null>(null);

  const roleSearchFields = [
    { key: 'name', label: t('common.role'), searchable: true },
    { key: 'description', label: t('common.description'), searchable: true },
    { key: 'permissions', label: t('common.permissions'), searchable: false },
  ];

  const columns: Column<Role>[] = [
    {
      key: 'name',
      header: t('common.role'),
      sortKey: 'name',
      hideable: false,
      render: (r) => <Link to={`/roles/${r.id}`} className="font-medium text-main transition-colors hover:text-accent">{r.name}</Link>,
    },
    { key: 'description', header: t('common.description'), render: (r) => <span className="text-muted">{r.description ?? '—'}</span> },
    {
      key: 'permissions',
      header: t('common.permissions'),
      // Count-only on purpose: a role can hold dozens of permissions and the full
      // list bloats the row — the detail page shows them all.
      render: (r) =>
        r.allPermissions ? (
          <Badge tone="accent">ALL</Badge>
        ) : r.permissions.length ? (
          <span>
            <span className="font-semibold text-accent">{r.permissions.length}</span>{' '}
            <span className="text-muted">{t('common.permCount')}</span>
          </span>
        ) : (
          <span className="text-muted">—</span>
        ),
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.identity') }, { label: t('nav.roles') }]}
      title={t('roles.title')}
      description={t('roles.desc')}
      actions={<Button variant="primary" onClick={() => setCreating(true)}>{t('roles.new')}</Button>}
    >

      <DataTable<Role>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(r) => r.id}
        storageKey="roles"
        loading={isLoading || (isFetching && !data)}
        emptyMessage={q ? t('roles.emptyFiltered') : t('roles.empty')}
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
            placeholder={t('roles.searchPh')}
            fields={roleSearchFields}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
          />
        }
        actionsHeader={t('common.actions')}
        actions={(r) => (
          <RowMenu
            ariaLabel={t('common.actions')}
            items={[
              ...(canWrite ? [{ label: t('common.permissions'), onClick: () => setAssignPermsTo(r), icon: LuShieldCheck }] : []),
              ...(canDelete ? [{ label: t('common.delete'), onClick: () => setDeleting(r), icon: LuTrash2, danger: true }] : []),
            ]}
          />
        )}
      />

      {creating && <CreateRoleModal onClose={() => setCreating(false)} />}
      {assignPermsTo && <AssignPermissionsModal role={assignPermsTo} onClose={() => setAssignPermsTo(null)} />}

      <ConfirmDialog
        open={!!deleting}
        title={t('roles.deleteTitle')}
        message={t('roles.deleteMsg', { name: deleting?.name ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delRole.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try { await delRole.mutateAsync(deleting.id); notify.success(t('roles.deleted')); setDeleting(null); } catch { /* global toast */ }
        }}
        onClose={() => setDeleting(null)}
      />
    </Page>
  );
}

function CreateRoleModal({ onClose }: { onClose: () => void }) {
  const { t } = useT();
  const create = useCreateRole();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    try {
      await create.mutateAsync({ name, description: description || undefined });
      notify.success(t('roles.created'));
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={t('roles.newTitle')}
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="primary" loading={create.isPending} onClick={submit}>{t('common.create')}</Button>
      </>}
    >
      <div className="flex flex-col gap-4">
        <TextField label={t('common.name')} value={name} onChange={(e) => setName(e.target.value)} placeholder={t('roles.namePh')} error={fieldErrors.name ?? null} required />
        <TextAreaField label={t('common.descriptionOptional')} value={description} onChange={(e) => setDescription(e.target.value)} error={fieldErrors.description ?? null} />
      </div>
    </Modal>
  );
}

function AssignPermissionsModal({ role, onClose }: { role: Role; onClose: () => void }) {
  const { t } = useT();
  const { data: permissions, isLoading } = usePermissions();
  const setPermissions = useSetRolePermissions();
  const [all, setAll] = useState(role.allPermissions);
  const [selected, setSelected] = useState<string[]>(role.permissions.map((p) => p.id));

  const items: CheckboxItem[] = (permissions?.items ?? []).map((p) => ({ id: p.id, label: p.name, description: p.description }));

  const submit = async () => {
    try {
      await setPermissions.mutateAsync({ id: role.id, data: all ? { all: true } : { permissionIds: selected } });
      notify.success(t('roles.permsUpdated'));
      onClose();
    } catch { /* global toast */ }
  };

  return (
    <Modal
      open
      title={t('roles.permsTitle', { name: role.name })}
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="primary" loading={setPermissions.isPending} onClick={submit}>{t('common.save')}</Button>
      </>}
    >
      <div className="flex flex-col gap-4">
        <Toggle checked={all} onChange={setAll} label={t('roles.allToggle')} />
        {all ? (
          <p className="text-sm text-muted">{t('roles.allHintShort')}</p>
        ) : isLoading ? (
          <div className="py-8 text-center text-sm text-muted">{t('roles.loadingPerms')}</div>
        ) : (
          <CheckboxList items={items} selectedIds={selected} onChange={setSelected} emptyMessage={t('roles.noPermsAvailable')} />
        )}
      </div>
    </Modal>
  );
}
