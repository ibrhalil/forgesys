import { useState } from 'react';
import { Page } from '../../../../components/Page';
import { DetailPanel, DetailField, PermissionBadges } from '../../../../components/detail/DetailPanel';
import { AssignSection } from '../../../../components/detail/AssignSection';
import { Badge } from '../../../../components/ui/Badge';
import { Button } from '../../../../components/ui/Button';
import { RowMenu } from '../../../../components/ui/RowMenu';
import { ConfirmDialog } from '../../../../components/ui/ConfirmDialog';
import { Modal } from '../../../../components/ui/Modal';
import { TextField } from '../../../../components/ui/Field';
import { DemoSection } from '../../components/DemoSection';
import type { SelectOption } from '../../../../lib/select';
import { LuPencil, LuTrash2, LuEllipsisVertical, LuShield, LuKeyRound, LuCheck } from 'react-icons/lu';

const AVAILABLE_ROLES: SelectOption<string>[] = [
  { value: 'iam:admin', label: 'iam:admin — Full Administrator' },
  { value: 'project:lead', label: 'project:lead — Sprint & Task Manager' },
  { value: 'notes:editor', label: 'notes:editor — Markdown Knowledge Base' },
  { value: 'apps:builder', label: 'apps:builder — Custom App Modeler' },
  { value: 'audit:viewer', label: 'audit:viewer — Access Log Auditor' },
];

function LiveDetailPage() {
  const [userName, setUserName] = useState('Alice Johnson');
  const userEmail = 'alice.johnson@acme.internal';
  const [userStatus, setUserStatus] = useState<'active' | 'locked'>('active');
  const [assignedRoles, setAssignedRoles] = useState<string[]>(['iam:admin', 'project:lead']);
  const [savingRoles, setSavingRoles] = useState(false);
  const [saveSuccessMsg, setSaveSuccessMsg] = useState(false);

  // Edit Profile Modal
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [draftName, setDraftName] = useState(userName);

  // Delete Confirm
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const handleRoleSave = async (newRoles: string[]) => {
    setSavingRoles(true);
    // Simulate server mutation
    await new Promise((r) => setTimeout(r, 600));
    setAssignedRoles(newRoles);
    setSavingRoles(false);
    setSaveSuccessMsg(true);
    setTimeout(() => setSaveSuccessMsg(false), 3000);
  };

  const handleSaveProfile = () => {
    setUserName(draftName);
    setEditModalOpen(false);
  };

  const handleDelete = () => {
    setDeleteLoading(true);
    setTimeout(() => {
      setDeleteLoading(false);
      setDeleteConfirmOpen(false);
      alert('User deleted (Simulated)');
    }, 800);
  };

  return (
    <div className="rounded-2xl border border-glass bg-bg/50 p-6 shadow-inner">
      <Page
        breadcrumb={[{ label: 'Directory', to: '#' }, { label: 'Users', to: '#' }, { label: userName }]}
        title={
          <div className="flex items-center gap-3">
            <span>{userName}</span>
            <Badge tone={userStatus === 'active' ? 'green' : 'warning'}>
              {userStatus === 'active' ? 'Active' : 'Locked'}
            </Badge>
          </div>
        }
        description={`Account profile and security privileges for ${userEmail}`}
        actions={
          <>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                setDraftName(userName);
                setEditModalOpen(true);
              }}
            >
              <LuPencil className="h-3.5 w-3.5" />
              <span>Edit Profile</span>
            </Button>

            <RowMenu
              ariaLabel="User options"
              icon={LuEllipsisVertical}
              items={[
                { label: 'Reset Password', onClick: () => alert('Reset password email dispatched.'), icon: LuKeyRound },
                {
                  label: userStatus === 'active' ? 'Lock Account' : 'Unlock Account',
                  onClick: () => setUserStatus((s) => (s === 'active' ? 'locked' : 'active')),
                  icon: LuShield,
                },
                { label: 'Delete Account', onClick: () => setDeleteConfirmOpen(true), icon: LuTrash2, danger: true },
              ]}
            />
          </>
        }
      >
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left / Overview Panel */}
          <div className="lg:col-span-1 space-y-6">
            <DetailPanel title="General Information">
              <dl className="space-y-4">
                <DetailField label="Display Name">{userName}</DetailField>
                <DetailField label="Email Address">{userEmail}</DetailField>
                <DetailField label="User ID">
                  <span className="font-mono text-xs text-muted">usr_9f8a7b6c5d4e</span>
                </DetailField>
                <DetailField label="Tenant Schema">
                  <span className="font-mono text-xs text-accent">acme_corp_main</span>
                </DetailField>
                <DetailField label="Created At">2025-01-10 09:30:15 UTC</DetailField>
                <DetailField label="Two-Factor Status">
                  <Badge tone="blue">Enforced</Badge>
                </DetailField>
              </dl>
            </DetailPanel>

            <DetailPanel title="Effective Direct Permissions">
              <PermissionBadges
                permissions={[
                  'iam:user:read',
                  'iam:user:write',
                  'iam:role:read',
                  'project:task:write',
                  'notes:read',
                  'platform:metrics:view',
                ]}
              />
            </DetailPanel>
          </div>

          {/* Right / Assignment & Activity Panels */}
          <div className="lg:col-span-2 space-y-6">
            <AssignSection<string>
              title="Assigned Roles & Security Policies"
              options={AVAILABLE_ROLES}
              selectedValues={assignedRoles}
              onSave={handleRoleSave}
              saving={savingRoles}
              placeholder="Select roles to assign to this member..."
              emptySelectedHint="No roles assigned. User will only possess baseline authenticated privileges."
            />

            {saveSuccessMsg && (
              <div className="rounded-lg border border-accent-green/30 bg-accent-green/10 px-4 py-2.5 text-xs text-accent-green font-medium flex items-center gap-2">
                <LuCheck className="h-4 w-4" />
                <span>Assigned roles updated and synced with tenant authorization cache.</span>
              </div>
            )}

            <DetailPanel title="Recent Security & Audit Events">
              <div className="space-y-3">
                {[
                  { event: 'AUTH_SESSION_REFRESH', ip: '192.168.1.104', time: '10 minutes ago' },
                  { event: 'IAM_ROLE_ASSIGNED', ip: '10.0.0.12 (Admin console)', time: '2 hours ago' },
                  { event: 'MFA_CHALLENGE_VERIFIED', ip: '192.168.1.104', time: 'Yesterday 18:42' },
                ].map((item, idx) => (
                  <div key={idx} className="flex items-center justify-between border-b border-glass pb-2.5 last:border-0 last:pb-0 text-xs">
                    <div>
                      <p className="font-semibold text-main">{item.event}</p>
                      <p className="text-muted text-[11px]">IP: {item.ip}</p>
                    </div>
                    <span className="text-muted/60">{item.time}</span>
                  </div>
                ))}
              </div>
            </DetailPanel>
          </div>
        </div>
      </Page>

      {/* Edit Profile Modal */}
      <Modal
        open={editModalOpen}
        title="Edit User Profile"
        onClose={() => setEditModalOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditModalOpen(false)}>Cancel</Button>
            <Button variant="primary" onClick={handleSaveProfile}>Save Changes</Button>
          </>
        }
      >
        <div className="space-y-4">
          <TextField label="Full Name" value={draftName} onChange={(e) => setDraftName(e.target.value)} />
          <TextField label="Email (Read-only)" value={userEmail} disabled />
        </div>
      </Modal>

      {/* Delete Confirm Dialog */}
      <ConfirmDialog
        open={deleteConfirmOpen}
        title="Delete User"
        message="Permanently remove this user account? All access privileges will be immediately revoked."
        confirmText="Delete Account"
        danger
        loading={deleteLoading}
        onConfirm={handleDelete}
        onClose={() => setDeleteConfirmOpen(false)}
      />
    </div>
  );
}

const DETAIL_PAGE_CODE = `import { Page } from 'components/Page';
import { DetailPanel, DetailField, PermissionBadges } from 'components/detail/DetailPanel';
import { AssignSection } from 'components/detail/AssignSection';
import { Badge } from 'components/ui/Badge';
import { Button } from 'components/ui/Button';
import { RowMenu } from 'components/ui/RowMenu';

export function UserDetailPage() {
  const { userId } = useParams();
  const { data: user } = useUser(userId);
  const { data: allRoles } = useRoles();
  const assignRoles = useAssignRoles();

  return (
    <Page
      breadcrumb={[
        { label: 'Directory', to: '/users' },
        { label: 'Users', to: '/users' },
        { label: user?.email ?? '' },
      ]}
      title={
        <div className="flex items-center gap-3">
          <span>{user?.name}</span>
          <Badge tone={user?.enabled ? 'green' : 'muted'}>
            {user?.enabled ? 'Active' : 'Disabled'}
          </Badge>
        </div>
      }
      description={user?.email}
      actions={
        <>
          <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
            Edit Profile
          </Button>
          <RowMenu
            ariaLabel="Options"
            icon={LuEllipsisVertical}
            items={[
              { label: 'Reset Password', onClick: handleResetPwd },
              { label: 'Delete User', onClick: () => setDeleting(true), danger: true },
            ]}
          />
        </>
      }
    >
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Definition List Panel */}
        <div className="lg:col-span-1 space-y-6">
          <DetailPanel title="General Information">
            <dl className="space-y-4">
              <DetailField label="Email">{user?.email}</DetailField>
              <DetailField label="Created At">{formatDate(user?.createdAt)}</DetailField>
            </dl>
          </DetailPanel>
        </div>

        {/* Multi-Select Assignment Section */}
        <div className="lg:col-span-2 space-y-6">
          <AssignSection
            title="Assigned Security Roles"
            options={roleOptions}
            selectedValues={user?.roleIds ?? []}
            onSave={(roles) => assignRoles.mutateAsync({ userId, roles })}
          />
        </div>
      </div>
    </Page>
  );
}`;

export function DetailPagePatternDemo() {
  return (
    <div className="space-y-10">
      <div>
        <div className="inline-flex items-center gap-1.5 rounded-md bg-accent/10 px-2.5 py-1 text-xs font-semibold text-accent mb-2">
          Page Pattern
        </div>
        <h1 className="text-2xl font-bold text-main">Entity Detail Page Pattern</h1>
        <p className="mt-1 text-sm text-muted">
          The standard detail view template for viewing and managing single entities (Users, Roles, Groups, Projects).
          Features definition list cards (`DetailPanel` + `DetailField`), multi-selection assignment sections (`AssignSection`), and head actions.
        </p>
      </div>

      <DemoSection
        title="Live Interactive Detail View"
        description="Try changing assigned roles (dirty-state button triggers save), editing the profile modal, or toggling account lock status."
        code={DETAIL_PAGE_CODE}
      >
        <LiveDetailPage />
      </DemoSection>
    </div>
  );
}
