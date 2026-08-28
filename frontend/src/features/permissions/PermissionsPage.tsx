import { PERMISSIONS } from '../../lib/permissions';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LuEye, LuPencil, LuTrash2 } from 'react-icons/lu';
import type { Permission } from './types';
import {
  usePermissionSearch, useCreatePermission, useUpdatePermission, useDeletePermission,
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
import { useListPageState } from '../../lib/useListPageState';
import { useAuthStore } from '../../store/authStore';

export function PermissionsPage() {
  const { t } = useT();
  const delPermission = useDeletePermission();
  const navigate = useNavigate();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.PERMISSION_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.PERMISSION_DELETE));

  // Server-side list (K-49): q + qFields + structured column filters all hit the
  // filter engine; the former client-side includes/sort/pagination are gone.
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
    filters,
    setFilters,
    q,
    listParams,
  } = useListPageState({ defaultSort: { field: 'name', dir: 'asc' }, storageKey: 'permissions', syncUrl: true });

  const { data, isLoading, isFetching, error, refetch } = usePermissionSearch(listParams);
  const hasFilterInput = q.length > 0 || filters.length > 0 || searchFields.length > 0;

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Permission | null>(null);
  const [deleting, setDeleting] = useState<Permission | null>(null);

  // Aligned with the backend's searchable registrations (PermissionService.FILTER_FIELDS).
  const permissionSearchFields = [
    { key: 'name', label: t('common.permission'), searchable: true },
    { key: 'description', label: t('common.description'), searchable: true },
  ];

  const columns: Column<Permission>[] = [
    {
      key: 'name',
      header: t('common.permission'),
      sortKey: 'name',
      filter: { field: 'name', control: 'text' },
      hideable: false,
      render: (p) => <span className="font-mono text-sm font-medium text-main">{p.name}</span>,
    },
    {
      key: 'description',
      header: t('common.description'),
      sortKey: 'description',
      filter: { field: 'description', control: 'text' },
      render: (p) => <span className="text-muted">{p.description ?? '—'}</span>,
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.identity') }, { label: t('nav.permissions') }]}
      title={t('common.permissions')}
      description={<>{t('permissions.desc')} <code className="font-mono text-accent">module:resource:action</code>.</>}
      actions={canWrite ? <Button variant="primary" onClick={() => setCreating(true)}>{t('permissions.new')}</Button> : undefined}
    >

      <DataTable<Permission>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(p) => p.id}
        storageKey="permissions"
        loading={isLoading}
        fetching={isFetching && !isLoading}
        error={error && !data ? error : undefined}
        onRetry={() => refetch()}
        emptyMessage={hasFilterInput ? t('permissions.emptyFiltered') : t('permissions.empty')}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={setPageSize}
        sort={sort}
        onSortChange={toggleSort}
        filters={filters}
        onFiltersChange={setFilters}
        toolbar={(
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder={t('permissions.searchPh')}
            fields={permissionSearchFields}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
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
