import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { User, Role, Group } from '../types';
import { useUsers, useRoles, useGroups, useCreateUser, useUpdateUser, useDeleteUser, useSetUserRoles, useSetUserGroups, useResetPassword } from '../hooks/useRbac';
import { useAuthStore } from '../store/authStore';
import { ApiError } from '../lib/api';
import { DataTable, type Column } from '../components/ui/DataTable';
import { Modal } from '../components/ui/Modal';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { TextField } from '../components/ui/Field';
import { CheckboxList, type CheckboxItem } from '../components/ui/CheckboxList';

const PAGE_SIZE = 10;

export function UsersPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isFetching } = useUsers({ page, size: PAGE_SIZE, sort: 'email' });
  const delUser = useDeleteUser();
  const navigate = useNavigate();
  const canManageSessions = useAuthStore((s) => s.hasAuthority('iam:user:write'));

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<User | null>(null);
  const [assignRolesTo, setAssignRolesTo] = useState<User | null>(null);
  const [assignGroupsTo, setAssignGroupsTo] = useState<User | null>(null);
  const [resetPwdFor, setResetPwdFor] = useState<User | null>(null);
  const [deleting, setDeleting] = useState<User | null>(null);

  const columns: Column<User>[] = [
    {
      key: 'name',
      header: 'User',
      render: (u) => (
        <div className="flex flex-col">
          <span className="font-medium text-main">{u.email}</span>
          {(u.firstName || u.lastName) && (
            <span className="text-xs text-muted">
              {[u.firstName, u.lastName].filter(Boolean).join(' ')}
            </span>
          )}
        </div>
      ),
    },
    { key: 'username', header: 'Username', render: (u) => <span className="text-muted">{u.username}</span> },
    {
      key: 'status',
      header: 'Status',
      render: (u) => (
        <div className="flex flex-wrap gap-1">
          <Badge tone={u.enabled ? 'green' : 'muted'}>{u.enabled ? 'Active' : 'Disabled'}</Badge>
          {u.emailVerified && <Badge tone="blue">Verified</Badge>}
        </div>
      ),
    },
    {
      key: 'roles',
      header: 'Roles',
      render: (u) =>
        u.roles.length ? (
          <div className="flex flex-wrap gap-1">{u.roles.map((r) => <Badge key={r.id} tone="accent">{r.name}</Badge>)}</div>
        ) : (
          <span className="text-muted">—</span>
        ),
    },
    {
      key: 'groups',
      header: 'Groups',
      render: (u) =>
        u.groups.length ? (
          <div className="flex flex-wrap gap-1">{u.groups.map((g) => <Badge key={g.id} tone="blue">{g.name}</Badge>)}</div>
        ) : (
          <span className="text-muted">—</span>
        ),
    },
  ];

  return (
    <div className="flex flex-col gap-6">
      <header className="flex items-center justify-between gap-4">
        <div>
          <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">Users</h1>
          <p className="mt-1 text-sm text-muted">Manage tenant users, roles and group memberships.</p>
        </div>
        <Button variant="primary" onClick={() => setCreating(true)}>+ New User</Button>
      </header>

      <DataTable<User>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(u) => u.id}
        loading={isLoading || (isFetching && !data)}
        emptyMessage="No users yet"
        page={data?.page ?? page}
        pageSize={data?.size ?? PAGE_SIZE}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        actionsHeader="Actions"
        actions={(u) => (
          <div className="flex justify-end gap-1">
            <Button size="sm" variant="ghost" onClick={() => setEditing(u)}>Edit</Button>
            <Button size="sm" variant="ghost" onClick={() => setAssignRolesTo(u)}>Roles</Button>
            <Button size="sm" variant="ghost" onClick={() => setAssignGroupsTo(u)}>Groups</Button>
            <Button size="sm" variant="ghost" onClick={() => setResetPwdFor(u)}>Password</Button>
            {canManageSessions && (
              <Button size="sm" variant="ghost" onClick={() => navigate(`/admin/users/${u.id}/sessions`)}>Sessions</Button>
            )}
            <Button size="sm" variant="danger" onClick={() => setDeleting(u)}>Delete</Button>
          </div>
        )}
      />

      {creating && <CreateUserModal onClose={() => setCreating(false)} />}
      {editing && <EditUserModal user={editing} onClose={() => setEditing(null)} />}
      {assignRolesTo && <AssignRolesModal user={assignRolesTo} onClose={() => setAssignRolesTo(null)} />}
      {assignGroupsTo && <AssignGroupsModal user={assignGroupsTo} onClose={() => setAssignGroupsTo(null)} />}
      {resetPwdFor && <ResetPasswordModal user={resetPwdFor} onClose={() => setResetPwdFor(null)} />}

      <ConfirmDialog
        open={!!deleting}
        title="Delete user"
        message={`Delete ${deleting?.email}? This soft-deletes the user, profile and account.`}
        confirmText="Delete"
        danger
        loading={delUser.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await delUser.mutateAsync(deleting.id);
            setDeleting(null);
          } catch {
            /* list invalidation handles UI; error surfaces as toast in a later pass */
          }
        }}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}

function CreateUserModal({ onClose }: { onClose: () => void }) {
  const create = useCreateUser();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [username, setUsername] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    try {
      await create.mutateAsync({
        email,
        password,
        username: username || undefined,
        firstName: firstName || undefined,
        lastName: lastName || undefined,
        enabled,
      });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not create user');
    }
  };

  return (
    <Modal
      open
      title="New User"
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant="primary" loading={create.isPending} onClick={submit}>Create</Button>
      </>}
    >
      <div className="flex flex-col gap-4">
        <TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="user@company.com" required />
        <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Min 8 characters" hint="Minimum 8 characters" required />
        <TextField label="Username (optional)" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Defaults to email prefix" />
        <div className="grid grid-cols-2 gap-3">
          <TextField label="First name (optional)" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
          <TextField label="Last name (optional)" value={lastName} onChange={(e) => setLastName(e.target.value)} />
        </div>
        <label className="flex items-center gap-2 text-sm text-main">
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} className="h-4 w-4 accent-[var(--color-accent)]" />
          Account enabled
        </label>
        {error && <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
      </div>
    </Modal>
  );
}

function EditUserModal({ user, onClose }: { user: User; onClose: () => void }) {
  const update = useUpdateUser();
  const [firstName, setFirstName] = useState(user.firstName ?? '');
  const [lastName, setLastName] = useState(user.lastName ?? '');
  const [enabled, setEnabled] = useState(user.enabled);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    try {
      await update.mutateAsync({ id: user.id, data: { firstName, lastName, enabled } });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not update user');
    }
  };

  return (
    <Modal
      open
      title={`Edit ${user.email}`}
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant="primary" loading={update.isPending} onClick={submit}>Save</Button>
      </>}
    >
      <div className="flex flex-col gap-4">
        <div className="grid grid-cols-2 gap-3">
          <TextField label="First name" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
          <TextField label="Last name" value={lastName} onChange={(e) => setLastName(e.target.value)} />
        </div>
        <label className="flex items-center gap-2 text-sm text-main">
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} className="h-4 w-4 accent-[var(--color-accent)]" />
          Account enabled
        </label>
        {error && <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
      </div>
    </Modal>
  );
}

function AssignRolesModal({ user, onClose }: { user: User; onClose: () => void }) {
  const { data: rolesData, isLoading } = useRoles({ size: 100 });
  const setRoles = useSetUserRoles();
  const [selected, setSelected] = useState<string[]>(user.roles.map((r) => r.id));
  const [error, setError] = useState<string | null>(null);

  const items: CheckboxItem[] = (rolesData?.items ?? []).map((r: Role) => ({ id: r.id, label: r.name, description: r.description }));

  const submit = async () => {
    setError(null);
    try {
      await setRoles.mutateAsync({ id: user.id, data: { roleIds: selected } });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not assign roles');
    }
  };

  return (
    <Modal
      open
      title={`Roles — ${user.email}`}
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

function AssignGroupsModal({ user, onClose }: { user: User; onClose: () => void }) {
  const { data: groupsData, isLoading } = useGroups({ size: 100 });
  const setGroups = useSetUserGroups();
  const [selected, setSelected] = useState<string[]>(user.groups.map((g) => g.id));
  const [error, setError] = useState<string | null>(null);

  const items: CheckboxItem[] = (groupsData?.items ?? []).map((g: Group) => ({ id: g.id, label: g.name, description: g.description }));

  const submit = async () => {
    setError(null);
    try {
      await setGroups.mutateAsync({ id: user.id, data: { groupIds: selected } });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not assign groups');
    }
  };

  return (
    <Modal
      open
      title={`Groups — ${user.email}`}
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant="primary" loading={setGroups.isPending} onClick={submit}>Save</Button>
      </>}
    >
      {isLoading ? (
        <div className="py-8 text-center text-sm text-muted">Loading groups…</div>
      ) : (
        <CheckboxList items={items} selectedIds={selected} onChange={setSelected} emptyMessage="No groups defined — create one in the Groups page" />
      )}
      {error && <div className="mt-3 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
    </Modal>
  );
}

function ResetPasswordModal({ user, onClose }: { user: User; onClose: () => void }) {
  const reset = useResetPassword();
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    try {
      await reset.mutateAsync({ id: user.id, data: { newPassword } });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not reset password');
    }
  };

  return (
    <Modal
      open
      title={`Reset password — ${user.email}`}
      onClose={onClose}
      size="sm"
      footer={<>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant="primary" loading={reset.isPending} onClick={submit}>Reset</Button>
      </>}
    >
      <div className="flex flex-col gap-3">
        <TextField label="New password" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="Min 8 characters" hint="Minimum 8 characters" required />
        {error && <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
      </div>
    </Modal>
  );
}
