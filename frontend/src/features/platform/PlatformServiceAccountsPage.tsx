import { useState } from 'react';
import { LuPlus } from 'react-icons/lu';
import type { ServiceAccount } from './types';
import { useCreateServiceAccount, useRevokeServiceAccount, useServiceAccounts } from './hooks';
import { PLATFORM_PERMISSIONS } from '../../lib/permissions';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { Page } from '../../components/Page';
import { SearchInput } from '../../components/ui/SearchInput';
import { PAGE_SIZE_OPTIONS } from '../../lib/pagination';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { RowMenu, type RowMenuItem } from '../../components/ui/RowMenu';
import { SelectInput } from '../../components/ui/SelectInput';
import { TextField } from '../../components/ui/Field';
import { formatDateTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import { useListPageState } from '../../lib/useListPageState';
import type { SelectOption } from '../../lib/select';

const SCOPE_OPTIONS: SelectOption<string>[] = Object.values(PLATFORM_PERMISSIONS).map((s) => ({
  value: s,
  label: s,
}));

function RawKeyModal({ rawKey, onClose }: { rawKey: string | null; onClose: () => void }) {
  const { t } = useT();
  const [copied, setCopied] = useState(false);

  return (
    <Modal
      open={!!rawKey}
      title={t('platform.svc.rawKeyTitle')}
      onClose={onClose}
      footer={
        <>
          <Button
            variant="secondary"
            onClick={async () => {
              await navigator.clipboard?.writeText(rawKey ?? '');
              setCopied(true);
            }}
          >
            {copied ? t('platform.svc.copied') : t('platform.svc.copy')}
          </Button>
          <Button variant="primary" onClick={onClose}>
            {t('common.close')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="m-0 text-sm text-muted">{t('platform.svc.rawKeyDesc')}</p>
        <code className="block break-all rounded-lg border border-glass bg-main/5 p-3 font-mono text-sm text-main">
          {rawKey}
        </code>
      </div>
    </Modal>
  );
}

function CreateServiceAccountModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (rawKey: string) => void;
}) {
  const { t } = useT();
  const create = useCreateServiceAccount();
  const [name, setName] = useState('');
  const [scopes, setScopes] = useState<SelectOption<string>[]>([]);
  const [expiresAt, setExpiresAt] = useState('');

  const submit = async () => {
    const created = await create.mutateAsync({
      name: name.trim(),
      scopes: scopes.map((s) => s.value),
      expiresAt: expiresAt ? new Date(expiresAt).toISOString() : undefined,
    });
    onClose();
    onCreated(created.rawKey);
  };

  return (
    <Modal
      open={open}
      title={t('platform.svc.createTitle')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button variant="primary" loading={create.isPending} disabled={!name.trim() || scopes.length === 0} onClick={submit}>
            {t('common.create')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField
          id="svc-name"
          label={t('platform.svc.name')}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <SelectInput
          label={t('platform.svc.scopes')}
          options={SCOPE_OPTIONS}
          value={scopes}
          onChange={(v) => setScopes((v as SelectOption<string>[]) ?? [])}
          isMulti
          placeholder={t('common.typeToSearch')}
        />
        <TextField
          id="svc-expires"
          label={t('platform.svc.expiresAt')}
          type="date"
          value={expiresAt}
          onChange={(e) => setExpiresAt(e.target.value)}
          hint={t('platform.svc.expiresHint')}
        />
      </div>
    </Modal>
  );
}

/**
 * Service accounts (K-50 F5): API-keyed programmatic identities. The raw key is
 * shown exactly once in a copy modal after creation — the list never carries it.
 */
export function PlatformServiceAccountsPage() {
  const { t } = useT();
  const {
    page, setPage, pageSize, setPageSize, sort, toggleSort,
    search, setSearch, listParams,
  } = useListPageState({ defaultSort: { field: 'createdDate', direction: 'desc' }, storageKey: 'platform-svc' });
  const { data, isLoading, isFetching, error, refetch } = useServiceAccounts(listParams);
  const revoke = useRevokeServiceAccount();

  const [createOpen, setCreateOpen] = useState(false);
  const [rawKey, setRawKey] = useState<string | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<ServiceAccount | null>(null);

  const columns: Column<ServiceAccount>[] = [
    {
      key: 'name',
      header: t('platform.svc.name'),
      sortKey: 'name',
      hideable: false,
      render: (a) => <span className="font-medium text-main">{a.name}</span>,
    },
    { key: 'keyPrefix', header: t('platform.svc.keyPrefix'), render: (a) => <span className="font-mono text-sm text-muted">{a.keyPrefix}</span> },
    {
      key: 'scopes',
      header: t('platform.svc.scopes'),
      render: (a) => (
        <div className="flex flex-wrap gap-1">
          {a.scopes.map((s) => (
            <Badge key={s} tone="accent"><span className="font-mono">{s}</span></Badge>
          ))}
        </div>
      ),
    },
    {
      key: 'expiresAt',
      header: t('platform.svc.expiresAt'),
      render: (a) => <span className="whitespace-nowrap text-muted">{a.expiresAt ? formatDateTime(a.expiresAt) : '—'}</span>,
    },
    {
      key: 'lastUsedAt',
      header: t('platform.svc.lastUsedAt'),
      render: (a) => <span className="whitespace-nowrap text-muted">{a.lastUsedAt ? formatDateTime(a.lastUsedAt) : t('platform.svc.never')}</span>,
    },
    {
      key: 'state',
      header: t('common.status'),
      render: (a) =>
        a.revokedAt ? (
          <Badge tone="danger">{t('platform.svc.revoked')}</Badge>
        ) : a.enabled ? (
          <Badge tone="green">{t('platform.status.ACTIVE')}</Badge>
        ) : (
          <Badge tone="muted">{t('platform.status.SUSPENDED')}</Badge>
        ),
    },
  ];

  return (
    <Page
      breadcrumb={[{ label: t('platform.console') }, { label: t('platform.nav.serviceAccounts') }]}
      title={t('platform.nav.serviceAccounts')}
      description={t('platform.svc.desc')}
      actions={
        <Button variant="primary" onClick={() => setCreateOpen(true)}>
          <LuPlus size={16} />
          {t('platform.svc.create')}
        </Button>
      }
    >
      <DataTable<ServiceAccount>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(a) => a.id}
        storageKey="platform-svc"
        loading={isLoading}
        fetching={isFetching && !isLoading}
        error={error && !data ? error : undefined}
        onRetry={() => refetch()}
        emptyMessage={search ? t('platform.companies.emptyFiltered') : t('platform.svc.empty')}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={setPageSize}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        sort={sort}
        onSortChange={toggleSort}
        toolbar={
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder={t('platform.svc.searchPh')}
          />
        }
        actions={(a) => {
          if (a.revokedAt) return undefined;
          const items: RowMenuItem[] = [
            { label: t('platform.svc.revoke'), danger: true, onClick: () => setRevokeTarget(a) },
          ];
          return <RowMenu ariaLabel={t('common.actions')} items={items} />;
        }}
        actionsHeader={t('common.actions')}
      />

      <CreateServiceAccountModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={setRawKey}
      />

      <RawKeyModal rawKey={rawKey} onClose={() => setRawKey(null)} />

      <ConfirmDialog
        open={!!revokeTarget}
        title={t('platform.svc.revokeTitle')}
        message={t('platform.svc.revokeConfirm', { name: revokeTarget?.name ?? '' })}
        confirmText={t('platform.svc.revoke')}
        cancelText={t('common.cancel')}
        danger
        loading={revoke.isPending}
        onConfirm={async () => {
          if (revokeTarget) await revoke.mutateAsync(revokeTarget.id);
          setRevokeTarget(null);
        }}
        onClose={() => setRevokeTarget(null)}
      />
    </Page>
  );
}
