import { PERMISSIONS } from '../../lib/permissions';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { Project, ProjectType } from './types';
import { useProjects, useProjectTypes, useProjectTypeLabels, useCreateProject, useDeleteProject } from './hooks';
import { notify, extractFieldErrors } from '../../lib/notify';
import { LuFolderOpen, LuTrash2 } from 'react-icons/lu';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { SearchInput } from '../../components/ui/SearchInput';
import { RowMenu } from '../../components/ui/RowMenu';
import { Modal } from '../../components/ui/Modal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Button } from '../../components/ui/Button';
import { Page } from '../../components/Page';
import { Badge } from '../../components/ui/Badge';
import { TextField } from '../../components/ui/Field';
import { TextAreaField } from '../../components/ui/TextArea';
import { SelectInput } from '../../components/ui/SelectInput';
import { useT } from '../../lib/i18n';
import { useListPageState } from '../../lib/useListPageState';
import { useAuthStore } from '../../store/authStore';

/**
 * Type options derive from the ACTIVE-module catalog (GET /projects/types, K-45) —
 * a disabled module's type never shows up as creatable. While the catalog loads the
 * list is empty (create waits for it — the backend enforces the gate regardless).
 */
function useTypeOptions() {
  const typeLabels = useProjectTypeLabels();
  const { data: catalog } = useProjectTypes();
  return (catalog ?? [])
    .filter((c) => typeLabels[c.type])
    .map((c) => ({ value: c.type, label: typeLabels[c.type] }));
}
export function ProjectsPage() {
  const { t } = useT();
  const navigate = useNavigate();
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
  } = useListPageState({ defaultSort: { field: 'name', dir: 'asc' }, storageKey: 'projects' });
  const { data, isLoading, isFetching } = useProjects({ page, size: pageSize, sorts: [sort], q: q || undefined });
  const delProject = useDeleteProject();
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.PROJECT_DELETE));
  const typeOptions = useTypeOptions();

  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<Project | null>(null);

  const projectSearchFields = [
    { key: 'name', label: t('projects.project'), searchable: true },
    { key: 'type', label: t('projects.type'), searchable: false },
    { key: 'description', label: t('common.description'), searchable: true },
  ];

  const columns: Column<Project>[] = [
    {
      key: 'name',
      header: t('projects.project'),
      sortKey: 'name',
      hideable: false,
      render: (p) => (
        <Link to={`/projects/${p.id}`} className="font-medium text-main transition-colors hover:text-accent">
          {p.name}
        </Link>
      ),
    },
    {
      key: 'type',
      header: t('projects.type'),
      sortKey: 'type',
      render: (p) => {
        const label = typeOptions.find((o) => o.value === p.type)?.label ?? p.type;
        const tone = p.type === 'TASKS' ? 'accent' : p.type === 'NOTES' ? 'blue' : 'green';
        return <Badge tone={tone}>{label}</Badge>;
      },
    },
    {
      key: 'description',
      header: t('common.description'),
      render: (p) => <span className="text-muted">{p.description ?? '—'}</span>,
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.projects') }]}
      title={t('projects.title')}
      description={t('projects.desc')}
      actions={<Button variant="primary" onClick={() => setCreating(true)}>{t('projects.new')}</Button>}
    >

      <DataTable<Project>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(p) => p.id}
        storageKey="projects"
        loading={isLoading || (isFetching && !data)}
        emptyMessage={q ? t('projects.emptyFiltered') : t('projects.empty')}
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
            placeholder={t('projects.searchPh')}
            fields={projectSearchFields}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
          />
        }
        actionsHeader={t('common.actions')}
        actions={(p) => (
          <RowMenu
            ariaLabel={t('common.actions')}
            items={[
              { label: t('projects.open'), onClick: () => navigate(`/projects/${p.id}`), icon: LuFolderOpen },
              ...(canDelete ? [{ label: t('common.delete'), onClick: () => setDeleting(p), icon: LuTrash2, danger: true }] : []),
            ]}
          />
        )}
      />

      {creating && <CreateProjectModal onClose={() => setCreating(false)} />}

      <ConfirmDialog
        open={!!deleting}
        title={t('projects.deleteTitle')}
        message={t('projects.deleteMsg', { name: deleting?.name ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delProject.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await delProject.mutateAsync(deleting.id);
            notify.success(t('projects.deleted'));
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

function CreateProjectModal({ onClose }: { onClose: () => void }) {
  const { t } = useT();
  const typeOptions = useTypeOptions();
  const create = useCreateProject();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  // No hardcoded default — the first catalog entry wins once the ACTIVE-module
  // catalog resolves (pm is always active in practice).
  const [type, setType] = useState<ProjectType | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    if (!type) return;
    try {
      const created = await create.mutateAsync({ name, description: description || undefined, type });
      notify.success(t('projects.created'));
      onClose();
      navigate(`/projects/${created.id}`);
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={t('projects.newTitle')}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" loading={create.isPending} disabled={!type} onClick={submit}>{t('common.create')}</Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField label={t('common.name')} value={name} onChange={(e) => setName(e.target.value)} placeholder={t('projects.namePh')} error={fieldErrors.name ?? null} required />
        <SelectInput
          label={t('projects.type')}
          placeholder={t('projects.typePlaceholder')}
          options={typeOptions}
          value={typeOptions.find((o) => o.value === type) ?? null}
          onChange={(next) => setType((next as { value: ProjectType } | null)?.value ?? null)}
        />
        <TextAreaField label={t('common.descriptionOptional')} value={description} onChange={(e) => setDescription(e.target.value)} error={fieldErrors.description ?? null} />
      </div>
    </Modal>
  );
}
