import { useState } from 'react';
import type { Role } from '../types';
import { useRoles, usePermissions, useCreateRole, useDeleteRole, useSetRolePermissions } from '../hooks/useRbac';
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

export function RolesPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isFetching } = useRoles({ page, size: PAGE_SIZE, sort: 'name' });
  const delRole = useDeleteRole();

  const [creating, setCreating] = useState(false);
  const [assignPermsTo, setAssignPermsTo] = useState<Role | null>(null);
  const [deleting, setDeleting] = useState<Role | null>(null);

  const columns: Column<Role>[] = [
    { key: 'name', header: 'Role', render: (r) => <span className="font-medium text-main">{r.name}</span> },
    { key: 'description', header: 'Description', render: (r) => <span className="text-muted">{r.description ?? '—'}</span> },
    {
      key: 'permissions',
      header: 'Permissions',
      render: (r) =>
        r.permissions.length ? (
          <div className="flex flex-wrap gap-1">{r.permissions.map((p) => <Badge key={p.id} tone="accent">{p.name}</Badge>)}</div>
        ) : (
          <span className="text-muted">—</span>
        ),
    },
  ];

  return (
    <div className="flex flex-col gap-6">
      <header className="flex items-center justify-between gap-4">
        <div>
          <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">Roles</h1>
          <p className="mt-1 text-sm text-muted">Group permissions into roles, then assign roles to users or groups.</p>
        </div>
        <Button variant="primary" onClick={() => setCreating(true)}>+ New Role</Button>
      </header>

      <DataTable<Role>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(r) => r.id}
        loading={isLoading || (isFetching && !data)}
        emptyMessage="No roles yet"
        page={data?.page ?? page}
        pageSize={data?.size ?? PAGE_SIZE}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        actionsHeader="Actions"
        actions={(r) => (
          <div className="flex justify-end gap-1">
            <Button size="sm" variant="ghost" onClick={() => setAssignPermsTo(r)}>Permissions</Button>
            <Button size="sm" variant="danger" onClick={() => setDeleting(r)}>Delete</Button>
          </div>
        )}
      />

      {creating && <CreateRoleModal onClose={() => setCreating(false)} />}
      {assignPermsTo && <AssignPermissionsModal role={assignPermsTo} onClose={() => setAssignPermsTo(null)} />}

      <ConfirmDialog
        open={!!deleting}
        title="Delete role"
        message={`Delete role "${deleting?.name}"? Users/groups holding it will lose its permissions.`}
        confirmText="Delete"
        danger
        loading={delRole.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try { await delRole.mutateAsync(deleting.id); setDeleting(null); } catch { /* noop */ }
        }}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}

function CreateRoleModal({ onClose }: { onClose: () => void }) {
  const create = useCreateRole();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    try {
      await create.mutateAsync({ name, description: description || undefined });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not create role');
    }
  };

  return (
    <Modal
      open
      title="New Role"
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant="primary" loading={create.isPending} onClick={submit}>Create</Button>
      </>}
    >
      <div className="flex flex-col gap-4">
        <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Editor" required />
        <TextAreaField label="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} />
        {error && <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
      </div>
    </Modal>
  );
}

function AssignPermissionsModal({ role, onClose }: { role: Role; onClose: () => void }) {
  const { data: permissions, isLoading } = usePermissions();
  const setPermissions = useSetRolePermissions();
  const [selected, setSelected] = useState<string[]>(role.permissions.map((p) => p.id));
  const [error, setError] = useState<string | null>(null);

  const items: CheckboxItem[] = (permissions ?? []).map((p) => ({ id: p.id, label: p.name, description: p.description }));

  const submit = async () => {
    setError(null);
    try {
      await setPermissions.mutateAsync({ id: role.id, data: { permissionIds: selected } });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not assign permissions');
    }
  };

  return (
    <Modal
      open
      title={`Permissions — ${role.name}`}
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant="primary" loading={setPermissions.isPending} onClick={submit}>Save</Button>
      </>}
    >
      {isLoading ? (
        <div className="py-8 text-center text-sm text-muted">Loading permissions…</div>
      ) : (
        <CheckboxList items={items} selectedIds={selected} onChange={setSelected} emptyMessage="No permissions available" />
      )}
      {error && <div className="mt-3 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
    </Modal>
  );
}
