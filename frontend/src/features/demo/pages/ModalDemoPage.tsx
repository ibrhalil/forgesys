import { useState } from 'react';
import { Modal } from '../../../components/ui/Modal';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { Button } from '../../../components/ui/Button';
import { DemoSection } from '../components/DemoSection';
import { LuTrash2, LuArchive, LuInfo } from 'react-icons/lu';

export function ModalDemoPage() {
  const [basicModalOpen, setBasicModalOpen] = useState(false);
  const [sizeModal, setSizeModal] = useState<'sm' | 'md' | 'lg' | null>(null);
  const [scrollModalOpen, setScrollModalOpen] = useState(false);
  const [noFooterModalOpen, setNoFooterModalOpen] = useState(false);

  // ConfirmDialog states
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmLoading, setConfirmLoading] = useState(false);

  const [dangerConfirmOpen, setDangerConfirmOpen] = useState(false);
  const [dangerLoading, setDangerLoading] = useState(false);

  const handleArchive = () => {
    setConfirmLoading(true);
    setTimeout(() => {
      setConfirmLoading(false);
      setConfirmOpen(false);
    }, 1200);
  };

  const handleDelete = () => {
    setDangerLoading(true);
    setTimeout(() => {
      setDangerLoading(false);
      setDangerConfirmOpen(false);
    }, 1200);
  };

  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-2xl font-bold text-main">Modal & ConfirmDialog</h1>
        <p className="mt-1 text-sm text-muted">
          Accessible dialog overlays with backdrop blur, focus trap, and keyboard navigation (Escape to close, Tab cycle).
        </p>
        <div className="mt-3 flex flex-wrap gap-2 text-xs">
          {[
            'components/ui/Modal.tsx',
            'components/ui/ConfirmDialog.tsx',
            'Size = "sm" | "md" | "lg"',
            'FocusTrap + Escape handling built-in',
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

      {/* 1. Basic Modal */}
      <DemoSection
        title="1. Standard Form / Content Modal"
        description="Standard modal with title, body, and bottom-right aligned action footer (Cancel ghost + Submit primary)."
        code={`const [open, setOpen] = useState(false);

<Button variant="primary" onClick={() => setOpen(true)}>
  Open Modal
</Button>

<Modal
  open={open}
  title="Create Workspace"
  onClose={() => setOpen(false)}
  footer={
    <>
      <Button variant="ghost" onClick={() => setOpen(false)}>
        Cancel
      </Button>
      <Button variant="primary" onClick={() => setOpen(false)}>
        Save Workspace
      </Button>
    </>
  }
>
  <p className="text-sm text-main">Modal content goes here...</p>
</Modal>`}
      >
        <div>
          <Button variant="primary" onClick={() => setBasicModalOpen(true)}>
            Open Standard Modal
          </Button>

          <Modal
            open={basicModalOpen}
            title="Create Workspace"
            onClose={() => setBasicModalOpen(false)}
            footer={
              <>
                <Button variant="ghost" onClick={() => setBasicModalOpen(false)}>
                  Cancel
                </Button>
                <Button variant="primary" onClick={() => setBasicModalOpen(false)}>
                  Save Workspace
                </Button>
              </>
            }
          >
            <div className="space-y-3 text-sm text-muted">
              <p className="text-main font-medium">Workspaces organize your team projects, notes, and custom apps.</p>
              <p>Workspaces are isolated within the current tenant schema. Permissions can be assigned per role or user group.</p>
            </div>
          </Modal>
        </div>
      </DemoSection>

      {/* 2. Modal Sizes */}
      <DemoSection
        title="2. Modal Sizes (sm / md / lg)"
        description="Pick from max-w-sm (Confirmations/Quick Prompts), max-w-lg (Forms, default), or max-w-2xl (Complex entity builders)."
        code={`// Size options: 'sm' | 'md' | 'lg'
<Modal size="sm" title="Small Modal" ... />
<Modal size="md" title="Medium Modal (Default)" ... />
<Modal size="lg" title="Large Modal" ... />`}
      >
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="secondary" onClick={() => setSizeModal('sm')}>
            Open Small (sm - 384px)
          </Button>
          <Button variant="secondary" onClick={() => setSizeModal('md')}>
            Open Medium (md - 512px)
          </Button>
          <Button variant="secondary" onClick={() => setSizeModal('lg')}>
            Open Large (lg - 672px)
          </Button>

          {sizeModal && (
            <Modal
              open={true}
              size={sizeModal}
              title={`Modal Size: ${sizeModal.toUpperCase()}`}
              onClose={() => setSizeModal(null)}
              footer={
                <Button variant="primary" onClick={() => setSizeModal(null)}>
                  Close
                </Button>
              }
            >
              <p className="text-sm text-muted">
                This is a <strong className="text-main">{sizeModal}</strong> sized modal dialog container.
              </p>
            </Modal>
          )}
        </div>
      </DemoSection>

      {/* 3. Long Scrollable Content */}
      <DemoSection
        title="3. Scrollable Overflow Body"
        description="The header and footer remain sticky while the modal body smoothly scrolls."
        code={`<Modal title="Audit Trail History" open={open} onClose={...}>
  <div className="space-y-4">
    {longListOfLogs.map(log => (...))}
  </div>
</Modal>`}
      >
        <div>
          <Button variant="secondary" onClick={() => setScrollModalOpen(true)}>
            Open Scrollable Modal
          </Button>

          <Modal
            open={scrollModalOpen}
            title="System Audit Trail & Access Logs"
            onClose={() => setScrollModalOpen(false)}
            footer={
              <Button variant="primary" onClick={() => setScrollModalOpen(false)}>
                Done
              </Button>
            }
          >
            <div className="space-y-3">
              {Array.from({ length: 15 }, (_, i) => (
                <div key={i} className="flex items-center justify-between border-b border-glass pb-2 text-xs">
                  <div>
                    <span className="font-semibold text-main">AUTH_SESSION_CREATED</span>
                    <span className="ml-2 text-muted">User user_{i + 1}@forgesys.internal</span>
                  </div>
                  <span className="text-muted/60">2026-08-24 14:{10 + i}:00</span>
                </div>
              ))}
            </div>
          </Modal>
        </div>
      </DemoSection>

      {/* 4. ConfirmDialog - Default vs Danger */}
      <DemoSection
        title="4. ConfirmDialog Component"
        description="Convenient pre-configured modal for non-destructive and destructive user confirmations."
        code={`import { ConfirmDialog } from 'components/ui/ConfirmDialog';

// 1. Standard Confirmation
<ConfirmDialog
  open={confirmOpen}
  title="Archive Project"
  message="Are you sure you want to archive this project? It can be restored later."
  confirmText="Archive"
  loading={loading}
  onConfirm={handleArchive}
  onClose={() => setConfirmOpen(false)}
/>

// 2. Destructive Deletion (danger tone)
<ConfirmDialog
  open={dangerConfirmOpen}
  title="Delete User"
  message="Permanently delete user 'alice@example.com'? This action cannot be undone."
  confirmText="Delete"
  danger={true}
  loading={loading}
  onConfirm={handleDelete}
  onClose={() => setDangerConfirmOpen(false)}
/>`}
      >
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="secondary" onClick={() => setConfirmOpen(true)}>
            <LuArchive className="h-4 w-4" />
            <span>Confirm Archive (Standard)</span>
          </Button>

          <Button variant="danger" onClick={() => setDangerConfirmOpen(true)}>
            <LuTrash2 className="h-4 w-4" />
            <span>Confirm Delete (Destructive)</span>
          </Button>

          <ConfirmDialog
            open={confirmOpen}
            title="Archive Project"
            message="Are you sure you want to archive this project? All associated tasks will be archived as well."
            confirmText="Archive"
            loading={confirmLoading}
            onConfirm={handleArchive}
            onClose={() => setConfirmOpen(false)}
          />

          <ConfirmDialog
            open={dangerConfirmOpen}
            title="Delete User Account"
            message="Are you sure you want to permanently delete user 'alice@example.com'? This action is irreversible."
            confirmText="Delete Account"
            danger={true}
            loading={dangerLoading}
            onConfirm={handleDelete}
            onClose={() => setDangerConfirmOpen(false)}
          />
        </div>
      </DemoSection>

      {/* 5. Info / Read-Only Modal (No Footer) */}
      <DemoSection
        title="5. Info Modal (No Footer)"
        description="Omit the footer prop for simple informational read-only modals."
        code={`<Modal
  open={open}
  title="Tenant Connection Details"
  onClose={() => setOpen(false)}
>
  <div className="space-y-2">
    <p>Database: postgresql-tenant-042</p>
    <p>Isolation: Schema-per-tenant</p>
  </div>
</Modal>`}
      >
        <div>
          <Button variant="ghost" onClick={() => setNoFooterModalOpen(true)}>
            <LuInfo className="h-4 w-4" />
            <span>View Connection Info</span>
          </Button>

          <Modal
            open={noFooterModalOpen}
            title="Tenant Connection Details"
            onClose={() => setNoFooterModalOpen(false)}
          >
            <div className="space-y-2 text-xs font-mono text-muted bg-main/5 p-4 rounded-lg">
              <p><span className="text-main font-semibold">Tenant ID:</span> tenant_forge_dev</p>
              <p><span className="text-main font-semibold">Schema:</span> tenant_forge_dev</p>
              <p><span className="text-main font-semibold">PostgreSQL:</span> v16.3</p>
              <p><span className="text-main font-semibold">Flyway Status:</span> Applied (V1..V8)</p>
            </div>
          </Modal>
        </div>
      </DemoSection>
    </div>
  );
}
