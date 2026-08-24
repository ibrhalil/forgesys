import { PERMISSIONS } from '../../lib/permissions';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LuEye, LuPencil, LuTrash2 } from 'react-icons/lu';
import type { Permission } from './types';
import {
  usePermissions, useCreatePermission, useUpdatePermission, useDeletePermission,
} from './hooks';
import { notify, extractFieldErrors } from '../../lib/notify';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { RowMenu } from '../../components/ui/RowMenu';
import { SearchInput } from '../../components/ui/SearchInput';
import { Modal } from '../../components/ui/Modal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Button } from '../../components/ui/Button';
import { Page } from '../../components/Page';
import { TextField } from '../../components/ui/Field';
import { TextAreaField } from '../../components/ui/TextArea';
import { useT } from '../../lib/i18n';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { useClientPagination } from '../../lib/useClientPagination';
import { useListPageState } from '../../lib/useListPageState';
import { useAuthStore } from '../../store/authStore';

export function PermissionsPage() {
  const { t } = useT();
  const { data: permissions, isLoading, isFetching } = usePermissions();
  const delPermission = useDeletePermission();
  const navigate = useNavigate();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.PERMISSION_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.PERMISSION_DELETE));

  const [query, setQuery] = useState('');
  // Client-paginated page: only the sort toggle comes from the list-state hook
  // (page/search state is inert here — pagination is local, filtering is instant).
  const { sort, toggleSort } = useListPageState({ defaultSort: { field: 'name', dir: 'asc' } });
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Permission | null>(null);
  const [deleting, setDeleting] = useState<Permission | null>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = permissions?.items ?? [];
    const filteredList = !q
      ? list
      : list.filter(
          (p) => p.name.toLowerCase().includes(q) || (p.description ?? '').toLowerCase().includes(q),
        );
    const dir = sort.dir === 'asc' ? 1 : -1;
    const key = sort.field as keyof Permission;
    return [...filteredList].sort((a, b) => {
      const av = (a[key] ?? '').toString().toLowerCase();
      const bv = (b[key] ?? '').toString().toLowerCase();
      return av.localeCompare(bv) * dir;
    });
  }, [permissions, query, sort]);

  // Full list arrives in one response — paginate locally for the standard footer UX.
  const pagination = useClientPagination(filtered, 10, 'permissions');
  const resetPage = () => pagination.setPage(0);

  const columns: Column<Permission>[] = [
    {
      key: 'name',
      header: t('common.permission'),
      sortKey: 'name',
      hideable: false,
      render: (p) => <span className="font-mono text-sm font-medium text-main">{p.name}</span>,
    },
    { key: 'description', header: t('common.description'), sortKey: 'description', render: (p) => <span className="text-muted">{p.description ?? '—'}</span> },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.identity') }, { label: t('nav.permissions') }]}
      title={t('common.permissions')}
      description={<>{t('permissions.desc')} <code className="font-mono text-accent">module:resource:action</code>.</>}
      actions={<Button variant="primary" onClick={() => setCreating(true)}>{t('permissions.new')}</Button>}
    >

      <DataTable<Permission>
        columns={columns}
        data={pagination.paged}
        rowKey={(p) => p.id}
        storageKey="permissions"
        loading={isLoading || (isFetching && !permissions)}
        emptyMessage={query ? t('permissions.emptyFiltered') : t('permissions.empty')}
        page={pagination.page}
        pageSize={pagination.pageSize}
        totalElements={pagination.totalElements}
        totalPages={pagination.totalPages}
        onPageChange={pagination.setPage}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={(size) => { pagination.setPageSize(size); resetPage(); }}
        sort={sort}
        onSortChange={toggleSort}
        toolbar={(
          <SearchInput
            value={query}
            onChange={(value) => { setQuery(value); resetPage(); }}
            placeholder={t('permissions.searchPh')}
          />
        )}
        actionsHeader={t('common.actions')}
        actions={(p) => (
          <RowMenu
            ariaLabel={t('common.actions')}
            items={[
              { label: t('common.view'), onClick: () => navigate(`/permissions/${p.id}`), icon: LuEye },
              ...(canWrite ? [{ label: t('common.edit'), onClick: () => setEditing(p), icon: LuPencil }] : []),
              ...(canDelete ? [{ label: t('common.delete'), onClick: () => setDeleting(p), icon: LuTrash2, danger: true }] : []),
            ]}
          />
        )}
      />

      {creating && <PermissionFormModal onClose={() => setCreating(false)} />}
      {editing && <PermissionFormModal permission={editing} onClose={() => setEditing(null)} />}

      <ConfirmDialog
        open={!!deleting}
        title={t('permissions.deleteTitle')}
        message={t('permissions.deleteMsg', { name: deleting?.name ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delPermission.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await delPermission.mutateAsync(deleting.id);
            notify.success(t('permissions.deleted'));
            setDeleting(null);
          } catch { /* global toast */ }
        }}
        onClose={() => setDeleting(null)}
      />
    </Page>
  );
}

function PermissionFormModal({ permission, onClose }: { permission?: Permission; onClose: () => void }) {
  const { t } = useT();
  const create = useCreatePermission();
  const update = useUpdatePermission();
  const [name, setName] = useState(permission?.name ?? '');
  const [description, setDescription] = useState(permission?.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const isEdit = !!permission;

  const submit = async () => {
    setFieldErrors({});
    try {
      if (isEdit && permission) {
        await update.mutateAsync({ id: permission.id, data: { name, description: description || undefined } });
        notify.success(t('permissions.updated'));
      } else {
        await create.mutateAsync({ name, description: description || undefined });
        notify.success(t('permissions.created'));
      }
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={isEdit ? t('permissions.editing', { name: permission!.name }) : t('permissions.newTitle')}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" loading={create.isPending || update.isPending} onClick={submit}>
            {isEdit ? t('common.save') : t('common.create')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField
          label={t('common.name')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t('permissions.namePh')}
          required
          hint={t('permissions.nameHint')}
          error={fieldErrors.name ?? null}
        />
        <TextAreaField label={t('common.descriptionOptional')} value={description} onChange={(e) => setDescription(e.target.value)} error={fieldErrors.description ?? null} />
      </div>
    </Modal>
  );
}
