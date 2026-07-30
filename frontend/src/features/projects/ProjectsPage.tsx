import { PERMISSIONS } from '../../lib/permissions';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { Project, ProjectType } from './types';
import { useProjects, useCreateProject, useDeleteProject } from './hooks';
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
import { useDebouncedValue } from '../../lib/useDebouncedValue';
import type { SortState } from '../../types';
import { useAuthStore } from '../../store/authStore';

const DEFAULT_PAGE_SIZE = 10;

function useTypeOptions() {
  const { t } = useT();
  return [
    { value: 'TASKS' as ProjectType, label: t('projects.typeTasks') },
    { value: 'NOTES' as ProjectType, label: t('projects.typeNotes') },
  ];
}
export function ProjectsPage() {
  const { t } = useT();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [sort, setSort] = useState<SortState>({ field: 'name', dir: 'asc' });
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search, 300);
  const { data, isLoading, isFetching } = useProjects({ page, size: pageSize, sorts: [sort], q: q || undefined });
  const delProject = useDeleteProject();
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.PROJECT_DELETE));
  const typeOptions = useTypeOptions();

  useEffect(() => {
    setPage(0);
  }, [q]);

  const handleSort = (field: string) => {
    setSort((prev) =>
      prev.field === field
        ? { field, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { field, dir: 'asc' },
    );
    setPage(0);
  };

  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<Project | null>(null);

  const columns: Column<Project>[] = [
    {
      key: 'name',
      header: t('projects.project'),
      sortKey: 'name',
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
        return <Badge tone={p.type === 'TASKS' ? 'accent' : 'blue'}>{label}</Badge>;
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
        loading={isLoading || (isFetching && !data)}
        emptyMessage={q ? t('projects.emptyFiltered') : t('projects.empty')}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        sort={sort}
        onSortChange={handleSort}
        toolbar={<SearchInput value={search} onChange={setSearch} placeholder={t('projects.searchPh')} />}
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
  const [type, setType] = useState<ProjectType>('TASKS');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
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
          <Button variant="primary" loading={create.isPending} onClick={submit}>{t('common.create')}</Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField label={t('common.name')} value={name} onChange={(e) => setName(e.target.value)} placeholder={t('projects.namePh')} error={fieldErrors.name ?? null} required />
        <SelectInput
          label={t('projects.type')}
          options={typeOptions}
          value={typeOptions.find((o) => o.value === type) ?? null}
          onChange={(next) => setType((next as { value: ProjectType } | null)?.value ?? 'TASKS')}
        />
        <TextAreaField label={t('common.descriptionOptional')} value={description} onChange={(e) => setDescription(e.target.value)} error={fieldErrors.description ?? null} />
      </div>
    </Modal>
  );
}
