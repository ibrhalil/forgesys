import { PERMISSIONS } from '../../../lib/permissions';
import { Link } from 'react-router-dom';
import { useState } from 'react';
import { LuPlus, LuTrash2 } from 'react-icons/lu';
import { useApps, useDeleteApp } from '../hooks';
import type { App } from '../types';
import { AppFormModal } from './AppFormModal';
import { Button } from '../../../components/ui/Button';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { useT } from '../../../lib/i18n';
import { useAuthStore } from '../../../store/authStore';
import { notify } from '../../../lib/notify';
import { formatDateTime } from '../../../lib/format';

/**
 * The APPS-type project body (K-45): the container's custom app collection (nested
 * list, single bounded page) with a create action anchored to this container. The
 * apps themselves are managed on their own detail pages.
 */
export function ProjectAppsPanel({ projectId }: { projectId: string }) {
  const { t } = useT();
  const { data, isLoading } = useApps({
    projectId,
    page: 0,
    size: 100,
    sorts: [{ field: 'name', dir: 'asc' }],
  });
  const delApp = useDeleteApp();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_DELETE));
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<App | null>(null);
  const apps = data?.items ?? [];

  return (
    <div className="rounded-xl border border-glass bg-surface">
      <div className="flex items-center justify-between gap-3 border-b border-glass px-5 py-3">
        <span className="text-sm font-medium text-main">{t('apps.inProject')}</span>
        {canWrite && (
          <Button variant="primary" size="sm" onClick={() => setCreating(true)}>
            <LuPlus size={14} />
            {t('apps.new')}
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="px-5 py-12 text-center text-muted">{t('common.loading')}</div>
      ) : apps.length === 0 ? (
        <div className="px-5 py-12 text-center text-muted">{t('apps.emptyProject')}</div>
      ) : (
        <ul className="divide-y divide-glass">
          {apps.map((a) => (
            <li key={a.id} className="flex items-center gap-3 px-5 py-3">
              <Link
                to={`/apps/${a.id}`}
                className="min-w-0 flex-1 truncate font-medium text-main transition-colors hover:text-accent"
              >
                {a.icon ? `${a.icon} ${a.name}` : a.name}
              </Link>
              <span className="hidden min-w-0 flex-1 truncate text-muted sm:block">{a.description ?? '—'}</span>
              <span className="shrink-0 text-xs text-muted">{formatDateTime(a.createdDate)}</span>
              {canDelete && (
                <button
                  type="button"
                  aria-label={t('common.delete')}
                  title={t('common.delete')}
                  onClick={() => setDeleting(a)}
                  className="shrink-0 rounded-md p-1.5 text-muted transition-colors hover:bg-danger/10 hover:text-danger"
                >
                  <LuTrash2 size={14} />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {creating && <AppFormModal projectId={projectId} onClose={() => setCreating(false)} />}

      <ConfirmDialog
        open={!!deleting}
        title={t('apps.deleteTitle')}
        message={t('apps.deleteMsg', { name: deleting?.name ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delApp.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await delApp.mutateAsync(deleting.id);
            notify.success(t('apps.deleted'));
            setDeleting(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}
