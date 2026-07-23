import { useState } from 'react';
import type { Group, Role } from '../types';
import { useGroups, useRoles, useCreateGroup, useDeleteGroup, useSetGroupRoles } from '../hooks/useRbac';
import { ApiError } from '../lib/api';
import { DataTable, type Column } from '../components/ui/DataTable';
import { Modal } from '../components/ui/Modal';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { TextField } from '../components/ui/Field';
import { TextAreaField } from '../components/ui/TextArea';
import { CheckboxList, type CheckboxItem } from '../components/ui/CheckboxList';

const PAGE_SIZE = 10;

export function GroupsPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isFetching } = useGroups({ page, size: PAGE_SIZE, sort: 'name' });
  const delGroup = useDeleteGroup();

  const [creating, setCreating] = useState(false);
  const [assignRolesTo, setAssignRolesTo] = useState<Group | null>(null);
  const [deleting, setDeleting] = useState<Group | null>(null);

  const columns: Column<Group>[] = [
    { key: 'name', header: 'Group', render: (g) => <span className="font-medium text-main">{g.name}</span> },
    { key: 'description', header: 'Description', render: (g) => <span className="text-muted">{g.description ?? '—'}</span> },
    {
      key: 'active',
      header: 'Status',
      render: (g) => <Badge tone={g.active ? 'green' : 'muted'}>{g.active ? 'Active' : 'Inactive'}</Badge>,
    },
    {
      key: 'roles',
      header: 'Roles',
      render: (g) =>
        g.roles.length ? (
          <div className="flex flex-wrap gap-1">{g.roles.map((r) => <Badge key={r.id} tone="accent">{r.name}</Badge>)}</div>
        ) : (
          <span className="text-muted">—</span>
        ),
    },
    { key: 'memberCount', header: 'Members', render: (g) => <span className="text-muted">{g.memberCount}</span> },
  ];

  return (
    <div className="flex flex-col gap-6">
      <header className="flex items-center justify-between gap-4">
        <div>
          <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">Groups</h1>
          <p className="mt-1 text-sm text-muted">Organize users into groups with shared roles. Add members from the Users page.</p>
        </div>
        <Button variant="primary" onClick={() => setCreating(true)}>+ New Group</Button>
      </header>

      <DataTable<Group>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(g) => g.id}
        loading={isLoading || (isFetching && !data)}
        emptyMessage="No groups yet"
        page={data?.page ?? page}
        pageSize={data?.size ?? PAGE_SIZE}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        actionsHeader="Actions"
        actions={(g) => (
          <div className="flex justify-end gap-1">
            <Button size="sm" variant="ghost" onClick={() => setAssignRolesTo(g)}>Roles</Button>
            <Button size="sm" variant="danger" onClick={() => setDeleting(g)}>Delete</Button>
          </div>
        )}
      />

      {creating && <CreateGroupModal onClose={() => setCreating(false)} />}
      {assignRolesTo && <AssignGroupRolesModal group={assignRolesTo} onClose={() => setAssignRolesTo(null)} />}

      <ConfirmDialog
        open={!!deleting}
        title="Delete group"
        message={`Delete group "${deleting?.name}"? Users will lose this group's roles.`}
        confirmText="Delete"
        danger
        loading={delGroup.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try { await delGroup.mutateAsync(deleting.id); setDeleting(null); } catch { /* noop */ }
        }}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}

function CreateGroupModal({ onClose }: { onClose: () => void }) {
  const create = useCreateGroup();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [active, setActive] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    try {
      await create.mutateAsync({ name, description: description || undefined, active });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not create group');
    }
  };

  return (
    <Modal
      open
      title="New Group"
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant="primary" loading={create.isPending} onClick={submit}>Create</Button>
      </>}
    >
      <div className="flex flex-col gap-4">
        <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Engineering" required />
        <TextAreaField label="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} />
        <label className="flex items-center gap-2 text-sm text-main">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="h-4 w-4 accent-[var(--color-accent)]" />
          Active
        </label>
        {error && <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
      </div>
    </Modal>
  );
}

function AssignGroupRolesModal({ group, onClose }: { group: Group; onClose: () => void }) {
  const { data: rolesData, isLoading } = useRoles({ size: 100 });
  const setRoles = useSetGroupRoles();
  const [selected, setSelected] = useState<string[]>(group.roles.map((r) => r.id));
  const [error, setError] = useState<string | null>(null);

  const items: CheckboxItem[] = (rolesData?.items ?? []).map((r: Role) => ({ id: r.id, label: r.name, description: r.description }));

  const submit = async () => {
    setError(null);
    try {
      await setRoles.mutateAsync({ id: group.id, data: { roleIds: selected } });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not assign roles');
    }
  };

  return (
    <Modal
      open
      title={`Roles — ${group.name}`}
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant="primary" loading={setRoles.isPending} onClick={submit}>Save</Button>
      </>}
    >
      {isLoading ? (
        <div className="py-8 text-center text-sm text-muted">Loading roles…</div>
      ) : (
        <CheckboxList items={items} selectedIds={selected} onChange={setSelected} emptyMessage="No roles defined — create one in the Roles page" />
      )}
      {error && <div className="mt-3 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
    </Modal>
  );
}
