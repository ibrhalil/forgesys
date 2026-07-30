import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Project, ProjectType } from '../types';
import { useProjects, useCreateProject, useDeleteProject } from '../hooks/useProjects';
import { ApiError } from '../lib/api';
import { DataTable, type Column } from '../components/ui/DataTable';
import { Modal } from '../components/ui/Modal';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { TextField } from '../components/ui/Field';
import { TextAreaField } from '../components/ui/TextArea';
import { SelectField } from '../components/ui/Select';

const PAGE_SIZE = 10;

const TYPE_OPTIONS: { value: ProjectType; label: string }[] = [
  { value: 'TASKS', label: 'Tasks — task board' },
  { value: 'NOTES', label: 'Notes — notes (soon)' },
];

export function ProjectsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const { data, isLoading, isFetching } = useProjects({ page, size: PAGE_SIZE, sort: 'name' });
  const delProject = useDeleteProject();

  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<Project | null>(null);

  const columns: Column<Project>[] = [
    {
      key: 'name',
      header: 'Project',
      render: (p) => (
        <button
          onClick={() => navigate(`/projects/${p.id}`)}
          className="text-left font-medium text-main transition-colors hover:text-accent"
        >
          {p.name}
        </button>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      render: (p) => <Badge tone={p.type === 'TASKS' ? 'accent' : 'blue'}>{p.type}</Badge>,
    },
    {
      key: 'description',
      header: 'Description',
      render: (p) => <span className="text-muted">{p.description ?? '—'}</span>,
    },
  ];

  return (
    <div className="flex flex-col gap-6">
      <header className="flex items-center justify-between gap-4">
        <div>
          <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">Projects</h1>
          <p className="mt-1 text-sm text-muted">Create a project, pick its type, then work inside it.</p>
        </div>
        <Button variant="primary" onClick={() => setCreating(true)}>+ New Project</Button>
      </header>

      <DataTable<Project>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(p) => p.id}
        loading={isLoading || (isFetching && !data)}
        emptyMessage="No projects yet — create your first one"
        page={data?.page ?? page}
        pageSize={data?.size ?? PAGE_SIZE}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        actionsHeader="Actions"
        actions={(p) => (
          <div className="flex justify-end gap-1">
            <Button size="sm" variant="ghost" onClick={() => navigate(`/projects/${p.id}`)}>Open</Button>
            <Button size="sm" variant="danger" onClick={() => setDeleting(p)}>Delete</Button>
          </div>
        )}
      />

      {creating && <CreateProjectModal onClose={() => setCreating(false)} />}

      <ConfirmDialog
        open={!!deleting}
        title="Delete project"
        message={`Delete project "${deleting?.name}"? Its tasks are removed with it.`}
        confirmText="Delete"
        danger
        loading={delProject.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await delProject.mutateAsync(deleting.id);
            setDeleting(null);
          } catch {
            /* noop */
          }
        }}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}

function CreateProjectModal({ onClose }: { onClose: () => void }) {
  const create = useCreateProject();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState<ProjectType>('TASKS');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    try {
      const created = await create.mutateAsync({ name, description: description || undefined, type });
      onClose();
      navigate(`/projects/${created.id}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not create project');
    }
  };

  return (
    <Modal
      open
      title="New Project"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={create.isPending} onClick={submit}>Create</Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Sprint Board" required />
        <SelectField label="Type" value={type} onChange={(e) => setType(e.target.value as ProjectType)}>
          {TYPE_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </SelectField>
        <TextAreaField label="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} />
        {error && <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
      </div>
    </Modal>
  );
}
