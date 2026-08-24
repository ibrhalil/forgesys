import { PERMISSIONS } from '../../lib/permissions';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Role } from '../roles/types';
import type { Group } from './types';
import { useGroups, useCreateGroup, useDeleteGroup, useSetGroupRoles } from './hooks';
import { useRoles } from '../roles/hooks';
import { notify, extractFieldErrors } from '../../lib/notify';
import { LuShield, LuTrash2 } from 'react-icons/lu';
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

export function GroupsPage() {
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
  } = useListPageState({ defaultSort: { field: 'name', dir: 'asc' }, storageKey: 'groups' });
  const { data, isLoading, isFetching } = useGroups({ page, size: pageSize, sorts: [sort], q: q || undefined });
  const delGroup = useDeleteGroup();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.GROUP_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.GROUP_DELETE));

  const [creating, setCreating] = useState(false);
  const [assignRolesTo, setAssignRolesTo] = useState<Group | null>(null);
  const [deleting, setDeleting] = useState<Group | null>(null);

  const groupSearchFields = [
    { key: 'name', label: t('common.group'), searchable: true },
    { key: 'description', label: t('common.description'), searchable: true },
    { key: 'status', label: t('common.status'), searchable: false },
    { key: 'roles', label: t('common.roles'), searchable: false },
  ];

  const columns: Column<Group>[] = [
    {
      key: 'name',
      header: t('common.group'),
      sortKey: 'name',
      hideable: false,
      render: (g) => <Link to={`/groups/${g.id}`} className="font-medium text-main transition-colors hover:text-accent">{g.name}</Link>,
    },
    { key: 'description', header: t('common.description'), render: (g) => <span className="text-muted">{g.description ?? '—'}</span> },
    {
      key: 'active',
      header: t('common.status'),
      render: (g) => <Badge tone={g.active ? 'green' : 'muted'}>{g.active ? t('common.active') : t('common.inactive')}</Badge>,
    },
    {
      key: 'roles',
      header: t('common.roles'),
      // Count-only on purpose (detail page shows the full list).
      render: (g) =>
        g.roles.length ? (
          <span>
            <span className="font-semibold text-accent">{g.roles.length}</span>{' '}
            <span className="text-muted">{t('common.roles').toLowerCase()}</span>
          </span>
        ) : (
          <span className="text-muted">—</span>
        ),
    },
    { key: 'memberCount', header: t('common.members'), render: (g) => <span className="font-semibold text-accent-blue">{g.memberCount}</span> },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.identity') }, { label: t('nav.groups') }]}
      title={t('groups.title')}
      description={t('groups.desc')}
      actions={<Button variant="primary" onClick={() => setCreating(true)}>{t('groups.new')}</Button>}
    >

      <DataTable<Group>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(g) => g.id}
        storageKey="groups"
        loading={isLoading || (isFetching && !data)}
        emptyMessage={q ? t('groups.emptyFiltered') : t('groups.empty')}
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
            placeholder={t('groups.searchPh')}
            fields={groupSearchFields}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
          />
        }
        actionsHeader={t('common.actions')}
        actions={(g) => (
          <RowMenu
            ariaLabel={t('common.actions')}
            items={[
              ...(canWrite ? [{ label: t('common.roles'), onClick: () => setAssignRolesTo(g), icon: LuShield }] : []),
              ...(canDelete ? [{ label: t('common.delete'), onClick: () => setDeleting(g), icon: LuTrash2, danger: true }] : []),
            ]}
          />
        )}
      />

      {creating && <CreateGroupModal onClose={() => setCreating(false)} />}
      {assignRolesTo && <AssignGroupRolesModal group={assignRolesTo} onClose={() => setAssignRolesTo(null)} />}

      <ConfirmDialog
        open={!!deleting}
        title={t('groups.deleteTitle')}
        message={t('groups.deleteMsg', { name: deleting?.name ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delGroup.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try { await delGroup.mutateAsync(deleting.id); notify.success(t('groups.deleted')); setDeleting(null); } catch { /* global toast */ }
        }}
        onClose={() => setDeleting(null)}
      />
    </Page>
  );
}

function CreateGroupModal({ onClose }: { onClose: () => void }) {
  const { t } = useT();
  const create = useCreateGroup();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [active, setActive] = useState(true);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    try {
      await create.mutateAsync({ name, description: description || undefined, active });
      notify.success(t('groups.created'));
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={t('groups.newTitle')}
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="primary" loading={create.isPending} onClick={submit}>{t('common.create')}</Button>
      </>}
    >
      <div className="flex flex-col gap-4">
        <TextField label={t('common.name')} value={name} onChange={(e) => setName(e.target.value)} placeholder={t('groups.namePh')} error={fieldErrors.name ?? null} required />
        <TextAreaField label={t('common.descriptionOptional')} value={description} onChange={(e) => setDescription(e.target.value)} error={fieldErrors.description ?? null} />
        <Toggle checked={active} onChange={setActive} label={t('common.activeLbl')} />
      </div>
    </Modal>
  );
}

function AssignGroupRolesModal({ group, onClose }: { group: Group; onClose: () => void }) {
  const { t } = useT();
  const { data: rolesData, isLoading } = useRoles({ size: 100 });
  const setRoles = useSetGroupRoles();
  const [selected, setSelected] = useState<string[]>(group.roles.map((r) => r.id));

  const items: CheckboxItem[] = (rolesData?.items ?? []).map((r: Role) => ({ id: r.id, label: r.name, description: r.description }));

  const submit = async () => {
    try {
      await setRoles.mutateAsync({ id: group.id, data: { roleIds: selected } });
      notify.success(t('common.rolesUpdated'));
      onClose();
    } catch { /* global toast */ }
  };

  return (
    <Modal
      open
      title={t('groups.rolesTitle', { name: group.name })}
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="primary" loading={setRoles.isPending} onClick={submit}>{t('common.save')}</Button>
      </>}
    >
      {isLoading ? (
        <div className="py-8 text-center text-sm text-muted">{t('users.loadingRoles')}</div>
      ) : (
        <CheckboxList items={items} selectedIds={selected} onChange={setSelected} emptyMessage={t('users.noRolesDefined')} />
      )}
    </Modal>
  );
}
