import { useEffect, useMemo, useState } from 'react';
import { LuEllipsisVertical, LuFilter, LuPlus, LuTrash2 } from 'react-icons/lu';
import { DetailPanel } from '../../../components/detail/DetailPanel';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { EmptyState } from '../../../components/ui/EmptyState';
import { RowMenu } from '../../../components/ui/RowMenu';
import { cn } from '../../../lib/cn';
import { useT } from '../../../lib/i18n';
import type { MessageKey } from '../../../lib/i18n';
import { PERMISSIONS } from '../../../lib/permissions';
import { useAuthStore } from '../../../store/authStore';
import { errorMessage, notify } from '../../../lib/notify';
import type { AppDetail, AppRecord, AppValueFilter, AppValueSort, AppView, ViewType } from '../types';
import { useDeleteRecord, useDeleteView, usePlanLimits, useRecords, useUpdateView, useViewRecords } from '../hooks';
import { applyViewQuery } from '../viewQuery';
import { useValueResolvers } from '../valueLabels';
import { RecordFormModal } from './RecordFormModal';
import { ViewModal } from './ViewModal';
import { ViewFilters } from './ViewFilters';
import { RecordTable } from './RecordTable';
import { RecordBoard } from './RecordBoard';
import { RecordCalendar } from './RecordCalendar';
import { RecordList } from './RecordList';
import { RecordGallery } from './RecordGallery';

const VIEW_DESC_KEYS: Record<ViewType, MessageKey> = {
  TABLE: 'apps.viewDesc.table',
  BOARD: 'apps.viewDesc.board',
  CALENDAR: 'apps.viewDesc.calendar',
  LIST: 'apps.viewDesc.list',
  GALLERY: 'apps.viewDesc.gallery',
};

/**
 * Records section orchestrator: view tabs (position, then name) + view CRUD +
 * the filter/sort editor + the renderer switch. Data flows through a single
 * bounded fetch (GET /records, first 1000) with filters/sorts applied
 * client-side — except TABLE views without a query, which keep the plain
 * server-paginated RecordTable (and with zero views, the section renders
 * exactly the pre-views behavior).
 */
export function RecordsPanel({ app }: { app: AppDetail }) {
  const { t } = useT();
  const canManageViews = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_WRITE));
  const canWriteRecords = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_RECORD_WRITE));

  const views = useMemo(
    () => [...app.views].sort((a, b) => a.position - b.position || a.name.localeCompare(b.name)),
    [app.views],
  );
  const [activeViewId, setActiveViewId] = useState<string | null>(null);
  const activeView = views.find((v) => v.id === activeViewId) ?? views[0] ?? null;

  const [transient, setTransient] = useState<{ filters: AppValueFilter[]; sorts: AppValueSort[] } | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [viewModal, setViewModal] = useState<{ mode: 'new' } | { mode: 'edit'; view: AppView } | null>(null);
  const [deletingView, setDeletingView] = useState<AppView | null>(null);
  const [creatingRecord, setCreatingRecord] = useState(false);
  const [editingRecord, setEditingRecord] = useState<AppRecord | null>(null);
  const [deletingRecord, setDeletingRecord] = useState<AppRecord | null>(null);

  const delView = useDeleteView(app.id);
  const delRecord = useDeleteRecord(app.id);
  const updateView = useUpdateView(app.id);

  // Switching views drops the transient override (it was scoped to the old view).
  const activeId = activeView?.id ?? null;
  useEffect(() => {
    setTransient(null);
    setFiltersOpen(false);
  }, [activeId]);

  const effectiveFilters = transient?.filters ?? activeView?.config?.filters;
  const effectiveSorts = transient?.sorts ?? activeView?.config?.sorts;
  const hasQuery = !!effectiveFilters?.length || !!effectiveSorts?.length;
  // TABLE without a query keeps its own server pagination + section chrome.
  const tableSelfMode = !activeView || (activeView.type === 'TABLE' && !hasQuery);

  const recordsQuery = useViewRecords(tableSelfMode ? undefined : app.id);
  // Record usage indicator: totalElements from a one-row probe (uniform across
  // self/client modes — the client-mode page is capped at 1000).
  const { data: recordCount } = useRecords(app.id, { page: 0, size: 1 });
  const { data: planLimits } = usePlanLimits();
  const visible = useMemo(
    () => applyViewQuery(recordsQuery.data?.items ?? [], app.properties, effectiveFilters, effectiveSorts),
    [recordsQuery.data, app.properties, effectiveFilters, effectiveSorts],
  );
  const resolve = useValueResolvers(app, visible);
  const fetchedCount = recordsQuery.data?.items.length ?? 0;
  const truncated = !tableSelfMode && (recordsQuery.data?.totalElements ?? 0) > fetchedCount;
  const filterCount = (effectiveFilters?.length ?? 0) + (effectiveSorts?.length ?? 0);

  const saveToView = async (filters: AppValueFilter[], sorts: AppValueSort[]) => {
    if (!activeView) return;
    try {
      await updateView.mutateAsync({
        viewId: activeView.id,
        data: {
          name: activeView.name,
          type: activeView.type,
          config: {
            ...(filters.length ? { filters } : {}),
            ...(sorts.length ? { sorts } : {}),
            ...(activeView.type === 'BOARD' && activeView.config?.groupBy
              ? { groupBy: activeView.config.groupBy }
              : {}),
            ...(activeView.type === 'CALENDAR' && activeView.config?.dateProperty
              ? { dateProperty: activeView.config.dateProperty }
              : {}),
          },
          position: activeView.position,
        },
      });
      notify.success(t('apps.viewUpdated'));
      setTransient(null);
      setFiltersOpen(false);
    } catch (e) {
      // No inline form here — surface everything, including field-level errors.
      notify.error(errorMessage(e));
    }
  };

  const renderer = (() => {
    if (!activeView || (activeView.type === 'TABLE' && !hasQuery)) {
      return <RecordTable app={app} onRequestEdit={setEditingRecord} />;
    }
    const common = {
      records: visible,
      isLoading: recordsQuery.isLoading,
      resolve,
      onRequestDelete: setDeletingRecord,
      onRequestEdit: setEditingRecord,
    };
    switch (activeView.type) {
      case 'TABLE':
        return (
          <RecordTable
            app={app}
            override={{ records: visible, isLoading: recordsQuery.isLoading }}
            onRequestDelete={setDeletingRecord}
            onRequestEdit={setEditingRecord}
          />
        );
      case 'BOARD':
        return <RecordBoard app={app} view={activeView} {...common} />;
      case 'CALENDAR':
        return (
          <RecordCalendar
            app={app}
            view={activeView}
            records={visible}
            isLoading={recordsQuery.isLoading}
            resolve={resolve}
            onRequestEdit={setEditingRecord}
          />
        );
      case 'LIST':
        return <RecordList app={app} {...common} />;
      default:
        return <RecordGallery app={app} {...common} />;
    }
  })();

  // Records are meaningless without properties — the single guard for every mode.
  if (app.properties.length === 0) {
    return (
      <DetailPanel title={t('apps.recordsSection')}>
        <EmptyState message={t('apps.emptyProperties')} />
      </DetailPanel>
    );
  }

  return (
    <>
      <DetailPanel title={t('apps.recordsSection')}>
        {!tableSelfMode && activeView && (
          <div className="mb-4 flex items-center justify-between gap-3">
            <p className="m-0 text-sm text-muted">{t(VIEW_DESC_KEYS[activeView.type])}</p>
            {canWriteRecords && (
              <Button variant="ghost" size="sm" onClick={() => setCreatingRecord(true)}>
                <LuPlus aria-hidden className="h-4 w-4" />
                {t('apps.newRecord')}
              </Button>
            )}
          </div>
        )}

        {views.length > 0 && (
          <div className="mb-4 flex flex-wrap items-center gap-1 border-b border-glass">
            {views.map((v) => {
              const active = v.id === activeView?.id;
              return (
                <button
                  key={v.id}
                  type="button"
                  onClick={() => setActiveViewId(v.id)}
                  aria-current={active}
                  className={cn(
                    '-mb-px border-b-2 px-3 py-2 text-sm transition-colors',
                    active
                      ? 'border-accent font-semibold text-accent'
                      : 'border-transparent text-muted hover:text-main',
                  )}
                >
                  {v.name}
                </button>
              );
            })}
            {canManageViews && activeView && (
              <RowMenu
                ariaLabel={t('common.actions')}
                icon={LuEllipsisVertical}
                items={[
                  { label: t('common.edit'), onClick: () => setViewModal({ mode: 'edit', view: activeView }) },
                  { label: t('common.delete'), onClick: () => setDeletingView(activeView), icon: LuTrash2, danger: true },
                ]}
              />
            )}
            {canManageViews && (
              <Button variant="ghost" size="sm" onClick={() => setViewModal({ mode: 'new' })}>
                <LuPlus aria-hidden className="h-4 w-4" />
                {t('apps.newView')}
              </Button>
            )}
            <Button
              variant="ghost"
              size="sm"
              className="ml-auto"
              aria-pressed={filtersOpen}
              onClick={() => setFiltersOpen((v) => !v)}
            >
              <LuFilter aria-hidden className="h-4 w-4" />
              {t('apps.filters')}
              {filterCount > 0 && <Badge tone="accent">{filterCount}</Badge>}
            </Button>
          </div>
        )}

        {filtersOpen && activeView && (
          <div className="mb-4">
            <ViewFilters
              app={app}
              seedFilters={effectiveFilters ?? []}
              seedSorts={effectiveSorts ?? []}
              canSave={canManageViews}
              saving={updateView.isPending}
              onApply={(filters, sorts) => {
                setTransient({ filters, sorts });
                setFiltersOpen(false);
              }}
              onSaveToView={saveToView}
              onClear={() => {
                setTransient(null);
                setFiltersOpen(false);
              }}
            />
          </div>
        )}

        {renderer}

        {planLimits && (
          <p className="m-0 mt-3 text-xs text-muted/80">
            {planLimits.maxRecordsPerApp >= 0
              ? t('apps.planRecords', {
                  used: recordCount?.totalElements ?? 0,
                  max: planLimits.maxRecordsPerApp,
                })
              : t('apps.planRecordsUnlimited', { used: recordCount?.totalElements ?? 0 })}
          </p>
        )}

        {truncated && (
          <p className="m-0 mt-3 text-xs text-muted/80">{t('apps.truncated', { count: fetchedCount })}</p>
        )}
      </DetailPanel>

      {viewModal && (
        <ViewModal
          appId={app.id}
          properties={app.properties}
          view={viewModal.mode === 'edit' ? viewModal.view : undefined}
          onCreated={(created) => setActiveViewId(created.id)}
          onClose={() => setViewModal(null)}
        />
      )}

      {creatingRecord && <RecordFormModal app={app} onClose={() => setCreatingRecord(false)} />}

      {editingRecord && (
        <RecordFormModal app={app} record={editingRecord} onClose={() => setEditingRecord(null)} />
      )}

      <ConfirmDialog
        open={!!deletingRecord}
        title={t('apps.deleteRecordTitle')}
        message={t('apps.deleteRecordMsg')}
        confirmText={t('common.delete')}
        danger
        loading={delRecord.isPending}
        onConfirm={async () => {
          if (!deletingRecord) return;
          try {
            await delRecord.mutateAsync(deletingRecord.id);
            notify.success(t('apps.recordDeleted'));
            setDeletingRecord(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeletingRecord(null)}
      />

      <ConfirmDialog
        open={!!deletingView}
        title={t('apps.deleteViewTitle')}
        message={t('apps.deleteViewMsg', { name: deletingView?.name ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delView.isPending}
        onConfirm={async () => {
          if (!deletingView) return;
          try {
            await delView.mutateAsync(deletingView.id);
            notify.success(t('apps.viewDeleted'));
            setDeletingView(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeletingView(null)}
      />
    </>
  );
}
