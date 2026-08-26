import { useState } from 'react';
import { Link } from 'react-router-dom';
import { LuTrash2 } from 'react-icons/lu';
import { Page } from '../../components/Page';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { SearchInput } from '../../components/ui/SearchInput';
import { RowMenu } from '../../components/ui/RowMenu';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { formatDateTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import { useListPageState } from '../../lib/useListPageState';
import { PERMISSIONS } from '../../lib/permissions';
import { useAuthStore } from '../../store/authStore';
import { notify } from '../../lib/notify';
import type { App } from './types';
import { useApps, useDeleteApp, usePlanLimits } from './hooks';
import { AppFormModal } from './components/AppFormModal';

export function AppsPage() {
  const { t } = useT();
  const { page, setPage, pageSize, setPageSize, sort, toggleSort, search, setSearch, searchFields, setSearchFields, filters, setFilters, q, listParams } =
    useListPageState({ defaultSort: { field: 'name', dir: 'asc' }, storageKey: 'apps' });
  const { data, isLoading, isFetching } = useApps(listParams);
  // Usage indicator: unfiltered total via a one-row probe (the list above is q-filtered).
  const { data: usage } = useApps({ page: 0, size: 1 });
  const { data: planLimits } = usePlanLimits();
  const delApp = useDeleteApp();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_DELETE));

  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<App | null>(null);

  // Aligned with the backend's searchable registrations (AppBuilderService.FILTER_FIELDS).
  const appSearchFields = [
    { key: 'name', label: t('common.name'), searchable: true },
    { key: 'description', label: t('common.description'), searchable: true },
    { key: 'projectName', label: t('projects.project'), searchable: true },
    { key: 'icon', label: t('apps.iconLabel'), searchable: false },
    { key: 'projectId', label: t('projects.project'), searchable: false },
    { key: 'createdDate', label: t('apps.createdDate'), searchable: false },
  ];

  const columns: Column<App>[] = [
    {
      key: 'name',
      header: t('common.name'),
      sortKey: 'name',
      filter: { field: 'name', control: 'text' },
      hideable: false,
      render: (a) => (
        <Link to={`/apps/${a.id}`} className="font-medium text-main transition-colors hover:text-accent">
          {a.icon ? `${a.icon} ${a.name}` : a.name}
        </Link>
      ),
    },
    {
      key: 'description',
      header: t('common.description'),
      sortKey: 'description',
      filter: { field: 'description', control: 'text' },
      render: (a) => <span className="text-muted">{a.description ?? '—'}</span>,
    },
    {
      key: 'project',
      header: t('projects.project'),
      sortKey: 'projectName',
      filter: { field: 'projectName', control: 'text' },
      render: (a) =>
        a.projectName ? (
          <Link to={`/projects/${a.projectId}`} className="text-muted transition-colors hover:text-accent">
            {a.projectName}
          </Link>
        ) : (
          <span className="text-muted">—</span>
        ),
    },
    { key: 'createdDate', header: t('apps.createdDate'), sortKey: 'createdDate', filter: { field: 'createdDate', control: 'date' }, render: (a) => <span className="text-muted">{formatDateTime(a.createdDate)}</span> },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.apps') }]}
      title={t('apps.title')}
      description={t('apps.desc')}
      actions={
        canWrite || (planLimits && usage) ? (
          <div className="flex items-center gap-4">
            {planLimits && usage && (
              <div
                className="flex flex-col gap-1"
                title={t('apps.planUsage', { used: usage.totalElements, max: planLimits.maxApps })}
              >
                <span className="text-xs font-medium text-muted">
                  {planLimits.maxApps >= 0
                    ? t('apps.planUsage', { used: usage.totalElements, max: planLimits.maxApps })
                    : t('apps.planUsageUnlimited', { used: usage.totalElements })}
                </span>
                {planLimits.maxApps > 0 && (
                  <div className="h-1.5 w-28 overflow-hidden rounded-full bg-main/10" aria-hidden>
                    <div
                      className="h-full rounded-full bg-accent"
                      style={{ width: `${Math.min(100, (usage.totalElements / planLimits.maxApps) * 100)}%` }}
                    />
                  </div>
                )}
              </div>
            )}
            {canWrite && (
              <Button variant="primary" onClick={() => setCreating(true)}>{t('apps.new')}</Button>
            )}
          </div>
        ) : undefined
      }
    >
      <DataTable<App>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(a) => a.id}
        storageKey="apps"
        loading={isLoading || (isFetching && !data)}
        emptyMessage={q ? t('apps.emptyFiltered') : t('apps.empty')}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={setPageSize}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        sort={sort}
        onSortChange={toggleSort}
        filters={filters}
        onFiltersChange={setFilters}
        toolbar={
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder={t('apps.searchPh')}
            fields={appSearchFields}
            selectedFields={searchFields}
            onSelectedFieldsChange={setSearchFields}
          />
        }
        actionsHeader={t('common.actions')}
        actions={(a) => (
          <RowMenu
            ariaLabel={t('common.actions')}
            items={
              canDelete
                ? [{ label: t('common.delete'), onClick: () => setDeleting(a), icon: LuTrash2, danger: true }]
                : []
            }
          />
        )}
      />

      {creating && <AppFormModal onClose={() => setCreating(false)} />}

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
    </Page>
  );
}
