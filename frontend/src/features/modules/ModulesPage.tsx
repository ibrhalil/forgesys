import { PERMISSIONS } from '../../lib/permissions';
import type { Module } from './types';
import { useModules, useActivateModule } from './hooks';
import { notify } from '../../lib/notify';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Page } from '../../components/Page';
import { useT } from '../../lib/i18n';
import { useAuthStore } from '../../store/authStore';
import { useClientPagination } from '../../lib/useClientPagination';

export function ModulesPage() {
  const { t } = useT();
  const { data: modules, isLoading, isFetching, error, refetch } = useModules();
  const activate = useActivateModule();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.MODULE_WRITE));

  // Small catalog — local pagination keeps the standard footer UX.
  const pagination = useClientPagination(modules ?? [], 10, 'modules');

  const columns: Column<Module>[] = [
    {
      key: 'name',
      header: t('common.name'),
      hideable: false,
      render: (m) => <span className="font-medium text-main">{m.name}</span>,
    },
    { key: 'key', header: t('modules.key'), render: (m) => <span className="font-mono text-sm text-muted">{m.key}</span> },
    {
      key: 'minPlan',
      header: t('modules.minPlan'),
      render: (m) => <Badge tone="blue">{m.minPlan.toUpperCase()}</Badge>,
    },
    {
      key: 'active',
      header: t('common.status'),
      render: (m) => (m.active ? <Badge tone="green">{t('modules.active')}</Badge> : <Badge tone="muted">{t('modules.inactive')}</Badge>),
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('nav.admin') }, { label: t('nav.modules') }]}
      title={t('modules.title')}
      description={t('modules.desc')}
    >
      <DataTable<Module>
        columns={columns}
        data={pagination.paged}
        rowKey={(m) => m.key}
        storageKey="modules"
        loading={isLoading}
        fetching={isFetching && !isLoading}
        error={error && !modules ? error : undefined}
        onRetry={() => refetch()}
        emptyMessage={t('modules.empty')}
        page={pagination.page}
        pageSize={pagination.pageSize}
        totalElements={pagination.totalElements}
        totalPages={pagination.totalPages}
        onPageChange={pagination.setPage}
        actionsHeader={t('common.actions')}
        actions={(m) =>
          !m.active && canWrite ? (
            <Button
              size="sm"
              variant="primary"
              disabled={!m.allowedByPlan}
              title={m.allowedByPlan ? undefined : t('modules.planRequired')}
              loading={activate.isPending && activate.variables === m.key}
              onClick={async () => {
                try {
                  await activate.mutateAsync(m.key);
                  notify.success(t('modules.activated', { name: m.name }));
                } catch { /* global toast */ }
              }}
            >
              {t('modules.activate')}
            </Button>
          ) : undefined
        }
      />
    </Page>
  );
}
