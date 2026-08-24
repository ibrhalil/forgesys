import { useState } from 'react';
import { Page } from '../../../../components/Page';
import { DataTable, type Column } from '../../../../components/ui/DataTable';
import { SearchInput, type SearchFieldOption } from '../../../../components/ui/SearchInput';
import { RowMenu } from '../../../../components/ui/RowMenu';
import { Button } from '../../../../components/ui/Button';
import { Badge } from '../../../../components/ui/Badge';
import { Modal } from '../../../../components/ui/Modal';
import { TextField } from '../../../../components/ui/Field';
import { SelectInput } from '../../../../components/ui/SelectInput';
import { ConfirmDialog } from '../../../../components/ui/ConfirmDialog';
import { DemoSection } from '../../components/DemoSection';
import { MOCK_USERS, paginate, sortBy, type MockUser } from '../../mockData';
import type { SelectOption } from '../../../../lib/select';
import type { SortState } from '../../../../types';
import { LuPlus, LuEye, LuPencil, LuTrash2 } from 'react-icons/lu';

const SEARCH_FIELDS: SearchFieldOption[] = [
  { key: 'email', label: 'Email', searchable: true },
  { key: 'name', label: 'Full Name', searchable: true },
  { key: 'username', label: 'Username', searchable: true },
  { key: 'status', label: 'Status', searchable: false },
];

const ROLE_OPTIONS: SelectOption<string>[] = [
  { value: 'admin', label: 'Admin (Full Access)' },
  { value: 'editor', label: 'Editor (Write & Publish)' },
  { value: 'viewer', label: 'Viewer (Read Only)' },
];

function LiveListPage() {
  const [users, setUsers] = useState<MockUser[]>(MOCK_USERS);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(5);
  const [sort, setSort] = useState<SortState>({ field: 'email', dir: 'asc' });
  const [search, setSearch] = useState('');
  const [searchFields, setSearchFields] = useState<string[]>([]);

  // Dialog & Modal states
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editUser, setEditUser] = useState<MockUser | null>(null);
  const [deleteUser, setDeleteUser] = useState<MockUser | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  // Form draft state
  const [formEmail, setFormEmail] = useState('');
  const [formFirstName, setFormFirstName] = useState('');
  const [formLastName, setFormLastName] = useState('');
  const [formRole, setFormRole] = useState<SelectOption<string> | null>(ROLE_OPTIONS[1]);

  const toggleSort = (field: string) => {
    setSort((s) => (s.field === field ? { field, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { field, dir: 'asc' }));
    setPage(0);
  };

  // Filter & paginate
  const activeKeys = searchFields.length === 0 ? ['email', 'name', 'username'] : searchFields;
  const filtered = users.filter((u) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return activeKeys.some((k) => {
      if (k === 'email') return u.email.toLowerCase().includes(q);
      if (k === 'name') return `${u.firstName} ${u.lastName}`.toLowerCase().includes(q);
      if (k === 'username') return u.username.toLowerCase().includes(q);
      return false;
    });
  });

  const sorted = sortBy(filtered, sort.field as keyof MockUser, sort.dir);
  const result = paginate(sorted, page, pageSize);

  const handleCreate = () => {
    if (!formEmail) return;
    const newUser: MockUser = {
      id: String(Date.now()),
      email: formEmail,
      firstName: formFirstName || 'New',
      lastName: formLastName || 'User',
      username: formEmail.split('@')[0],
      role: (formRole?.value as 'admin' | 'editor' | 'viewer') || 'viewer',
      status: 'active',
      emailVerified: true,
      createdAt: new Date().toISOString().split('T')[0],
      roleCount: 1,
      groupCount: 0,
    };
    setUsers([newUser, ...users]);
    setCreateModalOpen(false);
    setFormEmail('');
    setFormFirstName('');
    setFormLastName('');
  };

  const handleSaveEdit = () => {
    if (!editUser) return;
    setUsers(users.map((u) => (u.id === editUser.id ? { ...editUser, role: (formRole?.value as 'admin' | 'editor' | 'viewer') || editUser.role } : u)));
    setEditUser(null);
  };

  const handleDeleteConfirm = () => {
    if (!deleteUser) return;
    setDeleteLoading(true);
    setTimeout(() => {
      setUsers(users.filter((u) => u.id !== deleteUser.id));
      setDeleteLoading(false);
      setDeleteUser(null);
    }, 800);
  };

  const columns: Column<MockUser>[] = [
    {
      key: 'email',
      header: 'User',
      sortKey: 'email',
      hideable: false,
      render: (u) => (
        <div className="flex flex-col">
          <span className="font-semibold text-main">{u.email}</span>
          <span className="text-xs text-muted">{u.firstName} {u.lastName}</span>
        </div>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      render: (u) => (
        <Badge tone={u.role === 'admin' ? 'accent' : u.role === 'editor' ? 'blue' : 'muted'}>
          {u.role}
        </Badge>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (u) => (
        <Badge tone={u.status === 'active' ? 'green' : u.status === 'locked' ? 'warning' : 'muted'}>
          {u.status}
        </Badge>
      ),
    },
    {
      key: 'createdAt',
      header: 'Created',
      sortKey: 'createdAt',
      render: (u) => <span className="text-xs text-muted font-mono">{u.createdAt}</span>,
    },
  ];

  return (
    <div className="rounded-2xl border border-glass bg-bg/50 p-6 shadow-inner">
      <Page
        breadcrumb={[{ label: 'Directory' }, { label: 'Users' }]}
        title="Users & Members"
        description="Manage tenant members, assign security roles, and view authentication status."
        actions={
          <Button variant="primary" onClick={() => setCreateModalOpen(true)}>
            <LuPlus className="h-4 w-4" />
            <span>Invite User</span>
          </Button>
        }
      >
        <DataTable<MockUser>
          columns={columns}
          data={result.items}
          rowKey={(u) => u.id}
          storageKey="demo-list-pattern"
          page={result.page}
          pageSize={result.size}
          totalElements={result.totalElements}
          totalPages={result.totalPages}
          onPageChange={setPage}
          pageSizeOptions={[5, 10, 20]}
          onPageSizeChange={setPageSize}
          sort={sort}
          onSortChange={toggleSort}
          toolbar={
            <SearchInput
              value={search}
              onChange={(v) => { setSearch(v); setPage(0); }}
              placeholder="Search by name, email or username..."
              fields={SEARCH_FIELDS}
              selectedFields={searchFields}
              onSelectedFieldsChange={(f) => { setSearchFields(f); setPage(0); }}
            />
          }
          actionsHeader="Actions"
          actions={(u) => (
            <RowMenu
              ariaLabel="User actions"
              items={[
                { label: 'View Details', onClick: () => alert(`Viewing user: ${u.email}`), icon: LuEye },
                {
                  label: 'Edit User',
                  onClick: () => {
                    setEditUser(u);
                    setFormRole(ROLE_OPTIONS.find((r) => r.value === u.role) || ROLE_OPTIONS[0]);
                  },
                  icon: LuPencil,
                },
                { label: 'Delete User', onClick: () => setDeleteUser(u), icon: LuTrash2, danger: true },
              ]}
            />
          )}
        />
      </Page>

      {/* Create Modal */}
      <Modal
        open={createModalOpen}
        title="Invite New User"
        onClose={() => setCreateModalOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setCreateModalOpen(false)}>Cancel</Button>
            <Button variant="primary" onClick={handleCreate}>Send Invitation</Button>
          </>
        }
      >
        <div className="space-y-4">
          <TextField
            label="Email Address"
            placeholder="colleague@company.internal"
            value={formEmail}
            onChange={(e) => setFormEmail(e.target.value)}
          />
          <div className="grid grid-cols-2 gap-3">
            <TextField label="First Name" value={formFirstName} onChange={(e) => setFormFirstName(e.target.value)} />
            <TextField label="Last Name" value={formLastName} onChange={(e) => setFormLastName(e.target.value)} />
          </div>
          <SelectInput
            label="Primary Role"
            options={ROLE_OPTIONS}
            value={formRole}
            onChange={(v) => setFormRole(v as SelectOption<string> | null)}
          />
        </div>
      </Modal>

      {/* Edit Modal */}
      {editUser && (
        <Modal
          open={true}
          title={`Edit User: ${editUser.email}`}
          onClose={() => setEditUser(null)}
          footer={
            <>
              <Button variant="ghost" onClick={() => setEditUser(null)}>Cancel</Button>
              <Button variant="primary" onClick={handleSaveEdit}>Save Changes</Button>
            </>
          }
        >
          <div className="space-y-4">
            <TextField
              label="First Name"
              value={editUser.firstName}
              onChange={(e) => setEditUser({ ...editUser, firstName: e.target.value })}
            />
            <TextField
              label="Last Name"
              value={editUser.lastName}
              onChange={(e) => setEditUser({ ...editUser, lastName: e.target.value })}
            />
            <SelectInput
              label="Security Role"
              options={ROLE_OPTIONS}
              value={formRole}
              onChange={(v) => setFormRole(v as SelectOption<string> | null)}
            />
          </div>
        </Modal>
      )}

      {/* Delete Confirm */}
      <ConfirmDialog
        open={!!deleteUser}
        title="Delete User Account"
        message={`Are you sure you want to delete ${deleteUser?.email}? This action removes their access permanently.`}
        confirmText="Delete User"
        danger
        loading={deleteLoading}
        onConfirm={handleDeleteConfirm}
        onClose={() => setDeleteUser(null)}
      />
    </div>
  );
}

const LIST_PAGE_CODE = `import { Page } from 'components/Page';
import { DataTable, type Column } from 'components/ui/DataTable';
import { SearchInput } from 'components/ui/SearchInput';
import { RowMenu } from 'components/ui/RowMenu';
import { Button } from 'components/ui/Button';
import { ConfirmDialog } from 'components/ui/ConfirmDialog';
import { useListPageState } from 'lib/useListPageState';

export function EntityListPage() {
  const {
    page, setPage,
    pageSize, setPageSize,
    sort, toggleSort,
    search, setSearch,
    searchFields, setSearchFields,
    q,
  } = useListPageState({ storageKey: 'entities' });

  const { data, isLoading } = useEntities({ page, size: pageSize, sorts: [sort], q });
  const [deleteTarget, setDeleteTarget] = useState<Entity | null>(null);

  const columns: Column<Entity>[] = [
    { key: 'name', header: 'Name', sortKey: 'name', hideable: false },
    { key: 'status', header: 'Status', render: (row) => <Badge tone="green">{row.status}</Badge> },
  ];

  return (
    <Page
      breadcrumb={[{ label: 'Directory' }, { label: 'Entities' }]}
      title="Entities"
      description="Manage your tenant resources and access rights."
      actions={
        <Button variant="primary" onClick={() => navigate('/entities/new')}>
          Create Entity
        </Button>
      }
    >
      <DataTable<Entity>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(row) => row.id}
        storageKey="entities"
        loading={isLoading}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        pageSizeOptions={[10, 25, 50]}
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
            fields={SEARCH_FIELDS}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
          />
        }
        actions={(row) => (
          <RowMenu
            ariaLabel="Actions"
            items={[
              { label: 'View', onClick: () => navigate(\`/entities/\${row.id}\`), icon: LuEye },
              { label: 'Delete', onClick: () => setDeleteTarget(row), icon: LuTrash2, danger: true },
            ]}
          />
        )}
      />

      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete Item"
        message="Are you sure you want to delete this entity?"
        danger
        onConfirm={handleDelete}
        onClose={() => setDeleteTarget(null)}
      />
    </Page>
  );
}`;

export function ListPagePatternDemo() {
  return (
    <div className="space-y-10">
      <div>
        <div className="inline-flex items-center gap-1.5 rounded-md bg-accent/10 px-2.5 py-1 text-xs font-semibold text-accent mb-2">
          Page Pattern
        </div>
        <h1 className="text-2xl font-bold text-main">Full List Page Pattern</h1>
        <p className="mt-1 text-sm text-muted">
          The canonical CRUD list pattern used across Users, Roles, Groups, Projects, and Notes.
          Combines Page header, DataTable, Smart Search, Column Customization, RowMenu actions, Modals, and ConfirmDialog.
        </p>
      </div>

      <DemoSection
        title="Live Interactive List Page"
        description="Try creating a new user, sorting columns, searching specific fields, editing, and deleting. Full client-side lifecycle simulation."
        code={LIST_PAGE_CODE}
      >
        <LiveListPage />
      </DemoSection>
    </div>
  );
}
