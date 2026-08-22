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
import type { AppProperty } from './types';
import { useApp, useDeleteApp, useDeleteProperty } from './hooks';
import { AppFormModal } from './components/AppFormModal';
import { PropertyModal } from './components/PropertyModal';
import { RecordTable } from './components/RecordTable';

export function AppDetailPage() {
  const { appId } = useParams<{ appId: string }>();
  const navigate = useNavigate();
  const { t } = useT();
  const { data: app, isLoading } = useApp(appId);
  const delApp = useDeleteApp();
  const delProperty = useDeleteProperty(appId!);
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_DELETE));

  const [editing, setEditing] = useState(false);
  const [deletingApp, setDeletingApp] = useState(false);
  const [propertyModal, setPropertyModal] = useState<{ mode: 'new' } | { mode: 'edit'; property: AppProperty } | null>(null);
  const [deletingProperty, setDeletingProperty] = useState<AppProperty | null>(null);

  if (isLoading) return <DetailLoading message={t('apps.loadingApp')} />;
  if (!app) return <DetailNotFound message={t('apps.notFound')} backLabel={t('apps.backToApps')} backTo="/apps" />;

  return (
    <Page
      breadcrumb={[{ label: t('nav.apps'), to: '/apps' }, { label: app.name }]}
      title={app.name}
      description={app.description ?? undefined}
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
            <DetailField label={t('common.name')}>{app.name}</DetailField>
            <DetailField label={t('common.description')}>{app.description ?? <span className="text-muted">—</span>}</DetailField>
            <DetailField label={t('apps.createdDate')}>{formatDateTime(app.createdDate)}</DetailField>
          </div>
        </DetailPanel>

        <DetailPanel title={t('apps.propertiesSection')}>
          <div className="mb-4 flex items-center justify-between gap-3">
            <p className="m-0 text-sm text-muted">{t('apps.propertiesDesc')}</p>
            {canWrite && (
              <Button variant="ghost" size="sm" onClick={() => setPropertyModal({ mode: 'new' })}>
                <LuPlus aria-hidden className="h-4 w-4" />
                {t('apps.addProperty')}
              </Button>
            )}
          </div>
          {app.properties.length === 0 ? (
            <EmptyState message={t('apps.emptyProperties')} />
          ) : (
            <ul className="m-0 flex list-none flex-col gap-1 p-0">
              {app.properties.map((p) => (
                <li
                  key={p.id}
                  className="flex items-center gap-3 rounded-lg border border-glass px-3 py-2 transition-colors hover:bg-main/5"
                >
                  <span className="min-w-0 flex-1 truncate text-sm font-medium text-main">{p.name}</span>
                  <Badge tone="blue">{t(`apps.type.${p.type}`)}</Badge>
                  {p.required && <Badge tone="accent">{t('apps.requiredBadge')}</Badge>}
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

        <RecordTable app={app} />
      </div>

      {editing && <AppFormModal app={app} onClose={() => setEditing(false)} />}

      {propertyModal && (
        <PropertyModal appId={app.id} property={propertyModal.mode === 'edit' ? propertyModal.property : undefined} onClose={() => setPropertyModal(null)} />
      )}

      <ConfirmDialog
        open={deletingProperty !== null}
        title={t('apps.deletePropertyTitle')}
        message={t('apps.deletePropertyMsg', { name: deletingProperty?.name ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delProperty.isPending}
        onConfirm={async () => {
          if (!deletingProperty) return;
          try {
            await delProperty.mutateAsync(deletingProperty.id);
            notify.success(t('apps.propertyDeleted'));
            setDeletingProperty(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeletingProperty(null)}
      />

      <ConfirmDialog
        open={deletingApp}
        title={t('apps.deleteTitle')}
        message={t('apps.deleteMsg', { name: app.name })}
        confirmText={t('common.delete')}
        danger
        loading={delApp.isPending}
        onConfirm={async () => {
          try {
            await delApp.mutateAsync(app.id);
            notify.success(t('apps.deleted'));
            navigate('/apps');
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeletingApp(false)}
      />
    </Page>
  );
}
