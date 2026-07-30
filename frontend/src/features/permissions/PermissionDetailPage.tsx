import { PERMISSIONS } from '../../lib/permissions';
import { useState } from 'react';
import { LuEllipsisVertical, LuPencil, LuTrash2 } from 'react-icons/lu';
import { useAuthStore } from '../../store/authStore';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { usePermission, useUpdatePermission, useDeletePermission } from './hooks';
import { useRoles } from '../roles/hooks';
import { notify, extractFieldErrors } from '../../lib/notify';
import { Badge } from '../../components/ui/Badge';
import { Page } from '../../components/Page';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { RowMenu } from '../../components/ui/RowMenu';
import { TextField } from '../../components/ui/Field';
import { TextAreaField } from '../../components/ui/TextArea';
import { DetailPanel, DetailField } from '../../components/detail/DetailPanel';
import { useT } from '../../lib/i18n';
import { DetailLoading, DetailNotFound } from '../../components/detail/DetailFallback';

export function PermissionDetailPage() {
  const { t } = useT();
  const { permissionId } = useParams<{ permissionId: string }>();
  const { data: permission, isLoading } = usePermission(permissionId);
  const { data: rolesData } = useRoles({ size: 200, sort: 'name' });
  const del = useDeletePermission();
  const navigate = useNavigate();

  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.PERMISSION_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.PERMISSION_DELETE));
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);

  if (isLoading) return <DetailLoading message={t('permissions.loadingPerm')} />;
  if (!permission) {
    return <DetailNotFound message={t('permissions.notFound')} backLabel={t('permissions.backToPerms')} backTo="/permissions" />;
  }

  const holdingRoles = (rolesData?.items ?? []).filter((r) => r.permissions.some((p) => p.id === permission.id));

  return (
    <Page
      breadcrumb={[{ label: t('nav.identity') }, { label: t('nav.permissions'), to: '/permissions' }, { label: permission.name }]}
      title={<span className="font-mono">{permission.name}</span>}
      actions={(
        <>
          {/* Head pattern: at most one visible action + overflow menu (RowMenu).
              Empty items (no authority) render no trigger. */}
          {canWrite && <Button size="sm" variant="ghost" onClick={() => setEditing(true)}>
            <LuPencil className="h-3.5 w-3.5" />
            {t('common.edit')}
          </Button>}
          <RowMenu
            ariaLabel={t('common.actions')}
            icon={LuEllipsisVertical}
            items={
              canDelete
                ? [{ label: t('common.delete'), onClick: () => setDeleting(true), icon: LuTrash2, danger: true }]
                : []
            }
          />
        </>
      )}
    >

      <div className="grid gap-6 lg:grid-cols-2">
        <DetailPanel title={t('common.details')}>
          <dl className="grid grid-cols-1 gap-4">
            <DetailField label={t('common.name')}><span className="font-mono">{permission.name}</span></DetailField>
            <DetailField label={t('common.description')}>{permission.description}</DetailField>
          </dl>
        </DetailPanel>

        <DetailPanel title={t('permissions.assignedTo', { count: holdingRoles.length })}>
          {holdingRoles.length === 0 ? (
            <p className="text-sm text-muted">{t('permissions.noRoles')}</p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {holdingRoles.map((r) => (
                <Link key={r.id} to={`/roles/${r.id}`}>
                  <Badge tone="accent">{r.name}</Badge>
                </Link>
              ))}
            </div>
          )}
        </DetailPanel>
      </div>

      {editing && <EditPermissionModal permission={permission} onClose={() => setEditing(false)} />}

      <ConfirmDialog
        open={deleting}
        title={t('permissions.deleteTitle')}
        message={t('permissions.deleteMsg', { name: permission.name })}
        confirmText={t('common.delete')}
        danger
        loading={del.isPending}
        onConfirm={async () => {
          try {
            await del.mutateAsync(permission.id);
            notify.success(t('permissions.deleted'));
            navigate('/permissions');
          } catch { /* global toast */ }
        }}
        onClose={() => setDeleting(false)}
      />
    </Page>
  );
}

function EditPermissionModal({ permission, onClose }: { permission: { id: string; name: string; description: string | null }; onClose: () => void }) {
  const { t } = useT();
  const update = useUpdatePermission();
  const [name, setName] = useState(permission.name);
  const [description, setDescription] = useState(permission.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    try {
      await update.mutateAsync({ id: permission.id, data: { name, description: description || undefined } });
      notify.success(t('permissions.updated'));
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={t('permissions.editing', { name: permission.name })}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" loading={update.isPending} onClick={submit}>{t('common.save')}</Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField label={t('common.name')} value={name} onChange={(e) => setName(e.target.value)} hint={t('permissions.nameHint')} error={fieldErrors.name ?? null} />
        <TextAreaField label={t('common.descriptionOptional')} value={description} onChange={(e) => setDescription(e.target.value)} error={fieldErrors.description ?? null} />
      </div>
    </Modal>
  );
}
