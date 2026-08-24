import { useState } from 'react';
import { RowMenu } from '../../../components/ui/RowMenu';
import { EmptyState } from '../../../components/ui/EmptyState';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { DemoSection } from '../components/DemoSection';
import {
  LuEye,
  LuPencil,
  LuTrash2,
  LuEllipsisVertical,
  LuDownload,
  LuShare2,
  LuShield,
  LuKeyRound,
} from 'react-icons/lu';

export function RowMenuDemoPage() {
  const [lastAction, setLastAction] = useState<string | null>(null);
  const [isAdminRole, setIsAdminRole] = useState(true);

  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-2xl font-bold text-main">RowMenu & EmptyState</h1>
        <p className="mt-1 text-sm text-muted">
          Overflow action menus (with portal rendering to escape overflow clipping) and empty placeholder states.
        </p>
        <div className="mt-3 flex flex-wrap gap-2 text-xs">
          {[
            'components/ui/RowMenu.tsx',
            'components/ui/EmptyState.tsx',
            'Portal zIndex=60 (escapes overflow)',
            'Auto close on scroll/resize/escape',
          ].map((s) => (
            <code
              key={s}
              className="rounded-md border border-glass bg-main/[0.03] px-2 py-0.5 font-mono text-muted"
            >
              {s}
            </code>
          ))}
        </div>
      </div>

      {/* 1. Default Table Row Menu */}
      <DemoSection
        title="1. Table Row Actions Menu (Default Gear Icon)"
        description="Used in DataTable action columns. Renders in a document.body portal with fixed coordinates so table overflow doesn't clip the menu."
        code={`import { RowMenu } from 'components/ui/RowMenu';
import { LuEye, LuPencil, LuTrash2 } from 'react-icons/lu';

<RowMenu
  ariaLabel="User actions"
  items={[
    { label: 'View Profile', onClick: () => handleView(user.id), icon: LuEye },
    { label: 'Edit Roles',   onClick: () => handleEdit(user.id), icon: LuPencil },
    { label: 'Delete User',  onClick: () => handleDelete(user.id), icon: LuTrash2, danger: true },
  ]}
/>`}
      >
        <div className="space-y-4">
          <div className="rounded-xl border border-glass bg-surface p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="font-semibold text-main text-sm">alice@forgesys.internal</span>
              <Badge tone="green">Active</Badge>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-xs text-muted">Click gear:</span>
              <RowMenu
                ariaLabel="User actions"
                items={[
                  { label: 'View Profile', onClick: () => setLastAction('View Profile (Alice)'), icon: LuEye },
                  { label: 'Assign Roles', onClick: () => setLastAction('Assign Roles (Alice)'), icon: LuShield },
                  { label: 'Reset Password', onClick: () => setLastAction('Reset Password (Alice)'), icon: LuKeyRound },
                  { label: 'Delete Account', onClick: () => setLastAction('Delete Account (Alice)'), icon: LuTrash2, danger: true },
                ]}
              />
            </div>
          </div>

          {lastAction && (
            <p className="text-xs text-accent font-medium">
              Triggered action: <span className="underline">{lastAction}</span>
            </p>
          )}
        </div>
      </DemoSection>

      {/* 2. Page-Head Overflow Menu */}
      <DemoSection
        title="2. Page-Head Overflow Menu (Ellipsis Icon)"
        description="The Page header standard limits visible actions to two. Additional actions (Export, Share, Archive, Delete) sit inside an ellipsis overflow menu."
        code={`import { RowMenu } from 'components/ui/RowMenu';
import { LuEllipsisVertical, LuDownload, LuShare2, LuTrash2 } from 'react-icons/lu';

<RowMenu
  ariaLabel="More project actions"
  icon={LuEllipsisVertical}
  items={[
    { label: 'Export JSON',  onClick: exportJson, icon: LuDownload },
    { label: 'Share Link',   onClick: shareLink,  icon: LuShare2 },
    { label: 'Delete App',   onClick: deleteApp,  icon: LuTrash2, danger: true },
  ]}
/>`}
      >
        <div className="rounded-xl border border-glass bg-surface p-5 flex items-center justify-between">
          <div>
            <h2 className="text-base font-bold text-main">CRM Contacts Custom App</h2>
            <p className="text-xs text-muted">Created 3 days ago · 142 records</p>
          </div>

          <div className="flex items-center gap-2">
            <Button variant="primary" size="sm">
              New Record
            </Button>
            <RowMenu
              ariaLabel="More project actions"
              icon={LuEllipsisVertical}
              items={[
                { label: 'Export Data (CSV)', onClick: () => setLastAction('Export Data CSV'), icon: LuDownload },
                { label: 'Share Schema', onClick: () => setLastAction('Share Schema'), icon: LuShare2 },
                { label: 'Delete App', onClick: () => setLastAction('Delete App'), icon: LuTrash2, danger: true },
              ]}
            />
          </div>
        </div>
      </DemoSection>

      {/* 3. Permission-Gated Items */}
      <DemoSection
        title="3. Permission Filtering & Empty Menu Hide"
        description="Pass an array filtered by user permissions. If no items match, RowMenu renders nothing at all."
        code={`const items = [
  { label: 'View', onClick: viewDetails, icon: LuEye },
  ...(canWrite ? [{ label: 'Edit', onClick: editItem, icon: LuPencil }] : []),
  ...(canDelete ? [{ label: 'Delete', onClick: deleteItem, icon: LuTrash2, danger: true }] : []),
];

// If items.length === 0, returns null (no button rendered).
<RowMenu ariaLabel="Actions" items={items} />`}
      >
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <Button
              variant={isAdminRole ? 'primary' : 'secondary'}
              size="sm"
              onClick={() => setIsAdminRole((v) => !v)}
            >
              Toggle Role: {isAdminRole ? 'Admin (canWrite + canDelete)' : 'Viewer (read-only)'}
            </Button>
          </div>

          <div className="rounded-xl border border-glass bg-surface p-4 flex items-center justify-between">
            <span className="text-sm text-main">Audit Configuration Policy</span>
            <RowMenu
              ariaLabel="Policy actions"
              items={[
                { label: 'View Policy', onClick: () => setLastAction('View Policy'), icon: LuEye },
                ...(isAdminRole
                  ? [
                      { label: 'Edit Policy', onClick: () => setLastAction('Edit Policy'), icon: LuPencil },
                      { label: 'Delete Policy', onClick: () => setLastAction('Delete Policy'), icon: LuTrash2, danger: true },
                    ]
                  : []),
              ]}
            />
          </div>
        </div>
      </DemoSection>

      {/* 4. EmptyState Component */}
      <DemoSection
        title="4. EmptyState Component"
        description="Centered placeholder with folder icon, message, and optional action hint."
        code={`import { EmptyState } from 'components/ui/EmptyState';

// Standard empty
<EmptyState message="No projects found in this workspace." />

// Filtered empty with hint
<EmptyState
  message="No audit logs match your search criteria."
  hint="Try clearing your search query or adjusting the date range."
/>`}
      >
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="rounded-xl border border-glass bg-surface p-6">
            <span className="text-xs font-semibold text-muted uppercase tracking-wider">Initial Empty</span>
            <EmptyState message="No projects created yet." hint="Click 'New Project' above to create your first container." />
          </div>

          <div className="rounded-xl border border-glass bg-surface p-6">
            <span className="text-xs font-semibold text-muted uppercase tracking-wider">Filtered Empty</span>
            <EmptyState
              message="No users match your query."
              hint="Try clearing filters or checking other user directories."
            />
          </div>
        </div>
      </DemoSection>
    </div>
  );
}
