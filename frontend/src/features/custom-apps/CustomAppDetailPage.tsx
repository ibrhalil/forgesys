import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { LuEllipsisVertical, LuPencil, LuPlus, LuTrash2 } from 'react-icons/lu';
import { Page } from '../../components/Page';
import { DetailPanel, DetailField } from '../../components/detail/DetailPanel';
import { DetailLoading, DetailNotFound } from '../../components/detail/DetailFallback';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { RowMenu } from '../../components/ui/RowMenu';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { EmptyState } from '../../components/ui/EmptyState';
import { formatDateTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import { PERMISSIONS } from '../../lib/permissions';
import { useAuthStore } from '../../store/authStore';
import { notify } from '../../lib/notify';
import type { CustomAppProperty } from './types';
import { useCustomApp, useDeleteCustomApp, useDeleteProperty } from './hooks';
import { CustomAppFormModal } from './components/CustomAppFormModal';
import { PropertyModal } from './components/PropertyModal';
import { RecordsPanel } from './components/RecordsPanel';

export function CustomAppDetailPage() {
  const { customAppId } = useParams<{ customAppId: string }>();
  const navigate = useNavigate();
  const { t } = useT();
  const { data: customApp, isLoading } = useCustomApp(customAppId);
  const delApp = useDeleteCustomApp();
  const delProperty = useDeleteProperty(customAppId!);
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.CUSTOM_APP_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.CUSTOM_APP_DELETE));

  const [editing, setEditing] = useState(false);
  const [deletingApp, setDeletingApp] = useState(false);
  const [propertyModal, setPropertyModal] = useState<{ mode: 'new' } | { mode: 'edit'; property: CustomAppProperty } | null>(null);
  const [deletingProperty, setDeletingProperty] = useState<CustomAppProperty | null>(null);

  if (isLoading) return <DetailLoading message={t('customApps.loadingApp')} />;
  if (!customApp) return <DetailNotFound message={t('customApps.notFound')} backLabel={t('customApps.backToApps')} backTo="/custom-apps" />;

  return (
    <Page
      breadcrumb={[{ label: t('nav.customApps'), to: '/custom-apps' }, { label: customApp.name }]}
      title={customApp.icon ? `${customApp.icon} ${customApp.name}` : customApp.name}
      description={customApp.description ?? undefined}
      actions={
        <>
          {canWrite && (
            <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
              <LuPencil aria-hidden className="h-4 w-4" />
              {t('common.edit')}
            </Button>
          )}
          <RowMenu
            ariaLabel={t('common.actions')}
            icon={LuEllipsisVertical}
            items={
              canDelete
                ? [{ label: t('common.delete'), onClick: () => setDeletingApp(true), icon: LuTrash2, danger: true }]
                : []
            }
          />
        </>
      }
    >
      <div className="flex flex-col gap-6">
        <DetailPanel title={t('common.details')}>
          <div className="grid gap-4 sm:grid-cols-3">
            <DetailField label={t('common.name')}>{customApp.name}</DetailField>
            <DetailField label={t('common.description')}>{customApp.description ?? <span className="text-muted">—</span>}</DetailField>
            <DetailField label={t('customApps.createdDate')}>{formatDateTime(customApp.createdDate)}</DetailField>
          </div>
        </DetailPanel>

        <DetailPanel title={t('customApps.propertiesSection')}>
          <div className="mb-4 flex items-center justify-between gap-3">
            <p className="m-0 text-sm text-muted">{t('customApps.propertiesDesc')}</p>
            {canWrite && (
              <Button variant="ghost" size="sm" onClick={() => setPropertyModal({ mode: 'new' })}>
                <LuPlus aria-hidden className="h-4 w-4" />
                {t('customApps.addProperty')}
              </Button>
            )}
          </div>
          {customApp.properties.length === 0 ? (
            <EmptyState message={t('customApps.emptyProperties')} />
          ) : (
            <ul className="m-0 flex list-none flex-wrap gap-2 p-0">
              {customApp.properties.map((p) => (
                <li
                  key={p.id}
                  className="flex items-center gap-3 rounded-md border border-glass px-3 py-2 transition-colors hover:bg-main/5"
                >
                  <span className="max-w-56 min-w-0 truncate text-sm font-medium text-main">{p.name}</span>
                  <Badge tone="blue">{t(`customApps.type.${p.type}`)}</Badge>
                  {p.required && <Badge tone="accent">{t('customApps.requiredBadge')}</Badge>}
                  <span className="text-xs text-muted/70">#{p.position}</span>
                  {canWrite && (
                    <RowMenu
                      ariaLabel={t('common.actions')}
                      items={[
                        { label: t('common.edit'), onClick: () => setPropertyModal({ mode: 'edit', property: p }) },
                        { label: t('common.delete'), onClick: () => setDeletingProperty(p), icon: LuTrash2, danger: true },
                      ]}
                    />
                  )}
                </li>
              ))}
            </ul>
          )}
        </DetailPanel>

        <RecordsPanel customApp={customApp} />
      </div>

      {editing && <CustomAppFormModal customApp={customApp} onClose={() => setEditing(false)} />}

      {propertyModal && (
        <PropertyModal customAppId={customApp.id} property={propertyModal.mode === 'edit' ? propertyModal.property : undefined} onClose={() => setPropertyModal(null)} />
      )}

      <ConfirmDialog
        open={deletingProperty !== null}
        title={t('customApps.deletePropertyTitle')}
        message={t('customApps.deletePropertyMsg', { name: deletingProperty?.name ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delProperty.isPending}
        onConfirm={async () => {
          if (!deletingProperty) return;
          try {
            await delProperty.mutateAsync(deletingProperty.id);
            notify.success(t('customApps.propertyDeleted'));
            setDeletingProperty(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeletingProperty(null)}
      />

      <ConfirmDialog
        open={deletingApp}
        title={t('customApps.deleteTitle')}
        message={t('customApps.deleteMsg', { name: customApp.name })}
        confirmText={t('common.delete')}
        danger
        loading={delApp.isPending}
        onConfirm={async () => {
          try {
            await delApp.mutateAsync(customApp.id);
            notify.success(t('customApps.deleted'));
            navigate('/custom-apps');
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeletingApp(false)}
      />
    </Page>
  );
}
