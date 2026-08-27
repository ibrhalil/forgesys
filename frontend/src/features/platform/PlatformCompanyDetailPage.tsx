import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { LuArrowRightLeft, LuDoorOpen, LuEllipsisVertical } from 'react-icons/lu';
import type { CompanyStatus } from '../auth/types';
import type { PlatformModule } from './types';
import {
  useCompanyModules,
  useCompanyReport,
  useCompanySubscription,
  usePlatformCompany,
  useStartSwitch,
  useUpdateCompanyStatus,
  useUpdateModules,
  useUpdateSubscription,
} from './hooks';
import { PLATFORM_PERMISSIONS } from '../../lib/permissions';
import { usePlatformAuthStore } from '../../store/platformAuthStore';
import { Page } from '../../components/Page';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { RowMenu, type RowMenuItem } from '../../components/ui/RowMenu';
import { SelectInput } from '../../components/ui/SelectInput';
import { Spinner } from '../../components/ui/Spinner';
import { Toggle } from '../../components/ui/Toggle';
import { TextField } from '../../components/ui/Field';
import { DetailField, DetailPanel } from '../../components/detail/DetailPanel';
import { formatDateTime } from '../../lib/format';
import { useT } from '../../lib/i18n';
import type { SelectOption } from '../../lib/select';

const STATUS_TONE: Record<CompanyStatus, 'green' | 'warning' | 'danger' | 'muted'> = {
  ACTIVE: 'green',
  SUSPENDED: 'warning',
  TERMINATED: 'danger',
  PROVISIONING: 'muted',
};

// Mirror of the backend CompanyStatus.ALLOWED_TRANSITIONS (manual platform updates).
const ALLOWED_TRANSITIONS: Record<CompanyStatus, CompanyStatus[]> = {
  PROVISIONING: [],
  ACTIVE: ['SUSPENDED', 'TERMINATED'],
  SUSPENDED: ['ACTIVE', 'TERMINATED'],
  TERMINATED: [],
};

// Mirror of the backend PlanDefinition registry keys (same pattern as permissions.ts).
const PLAN_OPTIONS: SelectOption<string>[] = [
  { value: 'free', label: 'Free' },
  { value: 'pro', label: 'Pro' },
  { value: 'enterprise', label: 'Enterprise' },
];

// Tenant switch (F6 impersonation): the reason field lives here so typing never
// re-renders the whole detail page.
function SwitchTenantModal({
  open,
  companyId,
  onClose,
}: {
  open: boolean;
  companyId: string | undefined;
  onClose: () => void;
}) {
  const { t } = useT();
  const startSwitch = useStartSwitch(companyId ?? '');
  const [reason, setReason] = useState('');

  return (
    <Modal
      open={open}
      title={t('platform.company.switchTitle')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            variant="primary"
            loading={startSwitch.isPending}
            disabled={!reason.trim()}
            onClick={async () => {
              await startSwitch.mutateAsync({ reason: reason.trim() });
              onClose();
              setReason('');
            }}
          >
            {t('platform.company.switchOpen')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="m-0 text-sm text-muted">{t('platform.company.switchDesc')}</p>
        <TextField
          id="switch-reason"
          label={t('platform.company.switchReason')}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={t('platform.company.switchReasonPh')}
        />
      </div>
    </Modal>
  );
}

/**
 * Company detail (K-50): status actions, subscription plan change, module
 * activation toggles, usage report and the "enter tenant" switch (F6 token
 * exchange — opens the target subdomain with a one-time code).
 */
export function PlatformCompanyDetailPage() {
  const { companyId } = useParams<{ companyId: string }>();
  const { t } = useT();
  const hasAuthority = usePlatformAuthStore((s) => s.hasAuthority);

  const { data: company, isLoading } = usePlatformCompany(companyId);
  const { data: subscription } = useCompanySubscription(companyId);
  const { data: modules } = useCompanyModules(companyId);
  const canSeeReport = hasAuthority(PLATFORM_PERMISSIONS.TENANT_REPORT);
  const { data: report } = useCompanyReport(canSeeReport ? companyId : undefined);

  const updateStatus = useUpdateCompanyStatus(companyId ?? '');
  const updateSubscription = useUpdateSubscription(companyId ?? '');
  const updateModules = useUpdateModules(companyId ?? '');

  const canWriteStatus = hasAuthority(PLATFORM_PERMISSIONS.COMPANY_WRITE);
  const canLifecycle = hasAuthority(PLATFORM_PERMISSIONS.TENANT_LIFECYCLE);
  const canSwitch = hasAuthority(PLATFORM_PERMISSIONS.TENANT_ACCESS);
  const isActive = company?.status === 'ACTIVE';

  const [statusTarget, setStatusTarget] = useState<CompanyStatus | null>(null);
  const [planModalOpen, setPlanModalOpen] = useState(false);
  const [planKey, setPlanKey] = useState<string | null>(null);
  const [switchModalOpen, setSwitchModalOpen] = useState(false);
  // Local toggle state seeded from the server snapshot; committed with Save.
  const [moduleDraft, setModuleDraft] = useState<Record<string, boolean> | null>(null);
  const moduleState = moduleDraft ?? Object.fromEntries((modules ?? []).map((m) => [m.key, m.active]));
  const modulesDirty = useMemo(
    () => (modules ?? []).some((m) => moduleState[m.key] !== m.active),
    [modules, moduleState],
  );

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Spinner size="lg" className="border-glass border-t-accent" />
      </div>
    );
  }
  if (!company) {
    return <p className="py-16 text-center text-sm text-muted">{t('platform.companies.notFound')}</p>;
  }

  const overflowItems: RowMenuItem[] = [];
  if (canWriteStatus) {
    for (const target of ALLOWED_TRANSITIONS[company.status]) {
      overflowItems.push({
        label: t(`platform.status.${target}`),
        icon: LuArrowRightLeft,
        danger: target === 'TERMINATED',
        onClick: () => setStatusTarget(target),
      });
    }
  }
  if (canLifecycle) {
    overflowItems.push({
      label: t('platform.company.planChangeTitle'),
      icon: LuArrowRightLeft,
      onClick: () => {
        setPlanKey(subscription?.planKey ?? null);
        setPlanModalOpen(true);
      },
    });
  }

  const saveModules = async () => {
    const activations = (modules ?? []).map((m) => ({ key: m.key, active: moduleState[m.key] ?? false }));
    await updateModules.mutateAsync({ activations });
    setModuleDraft(null);
  };

  const statusLabel = (s: CompanyStatus) => t(`platform.status.${s}`);
  return (
    <Page
      breadcrumb={[
        { label: t('platform.console') },
        { label: t('platform.nav.companies'), to: '/platform/companies' },
        { label: company.name },
      ]}
      title={company.name}
      description={company.subdomain}
      actions={
        <>
          {canSwitch && isActive && (
            <Button variant="primary" onClick={() => setSwitchModalOpen(true)}>
              <LuDoorOpen size={16} />
              {t('platform.company.switchTitle')}
            </Button>
          )}
          {overflowItems.length > 0 && <RowMenu ariaLabel={t('common.actions')} icon={LuEllipsisVertical} items={overflowItems} />}
        </>
      }
    >
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <DetailPanel title={t('platform.company.statusTitle')}>
          <div className="grid grid-cols-2 gap-4">
            <DetailField label={t('common.status')}>
              <Badge tone={STATUS_TONE[company.status]}>{statusLabel(company.status)}</Badge>
            </DetailField>
            <DetailField label={t('platform.companies.subdomain')}>
              <span className="font-mono text-sm">{company.subdomain}</span>
            </DetailField>
          </div>
          {ALLOWED_TRANSITIONS[company.status].length > 0 && (
            <div className="mt-3 flex flex-wrap gap-2">
              {ALLOWED_TRANSITIONS[company.status].map((target) => (
                <Button
                  key={target}
                  size="sm"
                  variant={target === 'TERMINATED' ? 'danger' : 'secondary'}
                  disabled={!canWriteStatus}
                  onClick={() => setStatusTarget(target)}
                >
                  {statusLabel(target)}
                </Button>
              ))}
            </div>
          )}
        </DetailPanel>

        <DetailPanel title={t('platform.company.subscription')}>
          <div className="grid grid-cols-2 gap-4">
            <DetailField label={t('platform.company.plan')}>
              {subscription ? `${subscription.planName} (${subscription.planKey})` : '—'}
            </DetailField>
            <DetailField label={t('platform.company.planStatus')}>{subscription?.status ?? '—'}</DetailField>
            <DetailField label={t('platform.company.startedAt')}>
              {subscription?.startedAt ? formatDateTime(subscription.startedAt) : '—'}
            </DetailField>
          </div>
          {canLifecycle && (
            <div className="mt-4 flex justify-end gap-3">
              <Button
                size="sm"
                variant="secondary"
                disabled={!isActive}
                title={isActive ? undefined : t('platform.company.modulesBlocked')}
                onClick={() => {
                  setPlanKey(subscription?.planKey ?? null);
                  setPlanModalOpen(true);
                }}
              >
                {t('platform.company.planChangeTitle')}
              </Button>
            </div>
          )}
        </DetailPanel>

        <DetailPanel title={t('platform.company.modules')}>
          <div className="flex flex-col gap-3">
            {(modules ?? []).map((m: PlatformModule) => (
              <div key={m.key} className="flex items-center justify-between gap-3">
                <Toggle
                  checked={moduleState[m.key] ?? false}
                  onChange={(next) => setModuleDraft({ ...moduleState, [m.key]: next })}
                  disabled={!canLifecycle || !isActive || (!m.allowedByPlan && !moduleState[m.key])}
                  label={m.name}
                />
                <div className="flex shrink-0 items-center gap-2">
                  {!m.allowedByPlan && (
                    <Badge tone="warning">{t('platform.company.moduleNotAllowed')}</Badge>
                  )}
                  {m.active ? (
                    <Badge tone="green">{t('platform.company.moduleAllowed')}</Badge>
                  ) : null}
                </div>
              </div>
            ))}
            {canLifecycle && (
              <div className="mt-4 flex justify-end gap-3">
                <Button variant="secondary" disabled={!modulesDirty} onClick={() => setModuleDraft(null)}>
                  {t('common.cancel')}
                </Button>
                <Button
                  variant="primary"
                  loading={updateModules.isPending}
                  disabled={!modulesDirty || !isActive}
                  title={isActive ? undefined : t('platform.company.modulesBlocked')}
                  onClick={saveModules}
                >
                  {t('common.save')}
                </Button>
              </div>
            )}
          </div>
        </DetailPanel>

        {canSeeReport && (
          <DetailPanel title={t('platform.company.report')}>
            <div className="grid grid-cols-2 gap-4">
              <DetailField label={t('platform.company.reportUsers')}>
                <span className="text-xl font-semibold text-main">{report?.userCount ?? '—'}</span>
              </DetailField>
              <DetailField label={t('platform.company.reportProjects')}>
                <span className="text-xl font-semibold text-main">{report?.projectCount ?? '—'}</span>
              </DetailField>
              <DetailField label={t('platform.company.reportApps')}>
                <span className="text-xl font-semibold text-main">{report?.appCount ?? '—'}</span>
              </DetailField>
              <DetailField label={t('platform.company.reportNotes')}>
                <span className="text-xl font-semibold text-main">{report?.noteCount ?? '—'}</span>
              </DetailField>
            </div>
          </DetailPanel>
        )}
      </div>

      {/* Status change confirm */}
      {statusTarget && (
        <Modal
          open
          title={t('platform.company.statusChangeTitle')}
          onClose={() => setStatusTarget(null)}
          footer={
            <>
              <Button variant="secondary" onClick={() => setStatusTarget(null)}>
                {t('common.cancel')}
              </Button>
              <Button
                variant="primary"
                loading={updateStatus.isPending}
                onClick={async () => {
                  await updateStatus.mutateAsync({ status: statusTarget });
                  setStatusTarget(null);
                }}
              >
                {t('common.confirm')}
              </Button>
            </>
          }
        >
          <p className="m-0 text-sm text-main">
            {t('platform.company.statusChangeConfirm', { status: statusLabel(statusTarget) })}
          </p>
        </Modal>
      )}

      {/* Plan change */}
      <Modal
        open={planModalOpen}
        title={t('platform.company.planChangeTitle')}
        onClose={() => setPlanModalOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setPlanModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="primary"
              loading={updateSubscription.isPending}
              disabled={!planKey || planKey === subscription?.planKey}
              onClick={async () => {
                if (!planKey) return;
                await updateSubscription.mutateAsync({ planKey });
                setPlanModalOpen(false);
              }}
            >
              {t('common.save')}
            </Button>
          </>
        }
      >
        <SelectInput
          label={t('platform.company.plan')}
          options={PLAN_OPTIONS}
          value={PLAN_OPTIONS.find((o) => o.value === planKey) ?? null}
          onChange={(v) => setPlanKey((v as SelectOption<string> | null)?.value ?? null)}
        />
      </Modal>

      {/* Tenant switch (impersonation) */}
      <SwitchTenantModal open={switchModalOpen} companyId={companyId} onClose={() => setSwitchModalOpen(false)} />
    </Page>
  );
}
