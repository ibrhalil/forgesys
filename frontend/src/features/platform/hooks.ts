import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notify } from '../../lib/notify';
import { useT } from '../../lib/i18n';
import type { PageParams } from '../../types';
import { platformAuditApi, platformCompaniesApi, platformMailApi, platformServiceAccountsApi } from './api';
import type {
  CompanyModulesUpdateRequest,
  CompanyStatusUpdateRequest,
  PlatformAuditParams,
  PlatformCompanyParams,
  PlatformMailSampleData,
  PlatformMailTestSendRequest,
  PlatformSwitchStartRequest,
  ServiceAccountCreateRequest,
  SubscriptionUpdateRequest,
} from './types';

// Platform collections use a two-level key: ['platform', <collection>, params].

export function usePlatformCompanies(params: PlatformCompanyParams = {}) {
  return useQuery({
    queryKey: ['platform', 'companies', params],
    queryFn: () => platformCompaniesApi.searchOrList(params),
  });
}

export function usePlatformCompany(id: string | undefined) {
  return useQuery({
    queryKey: ['platform', 'companies', id],
    queryFn: () => platformCompaniesApi.get(id!),
    enabled: !!id,
  });
}

export function useCompanySubscription(id: string | undefined) {
  return useQuery({
    queryKey: ['platform', 'companies', id, 'subscription'],
    queryFn: () => platformCompaniesApi.getSubscription(id!),
    enabled: !!id,
  });
}

export function useCompanyModules(id: string | undefined) {
  return useQuery({
    queryKey: ['platform', 'companies', id, 'modules'],
    queryFn: () => platformCompaniesApi.getModules(id!),
    enabled: !!id,
  });
}

export function useCompanyReport(id: string | undefined) {
  return useQuery({
    queryKey: ['platform', 'companies', id, 'report'],
    queryFn: () => platformCompaniesApi.getReport(id!),
    enabled: !!id,
  });
}

export function useUpdateCompanyStatus(id: string) {
  const { t } = useT();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CompanyStatusUpdateRequest) => platformCompaniesApi.updateStatus(id, data),
    onSuccess: () => {
      notify.success(t('platform.company.statusChanged'));
      void qc.invalidateQueries({ queryKey: ['platform', 'companies'] });
    },
  });
}

export function useUpdateSubscription(id: string) {
  const { t } = useT();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: SubscriptionUpdateRequest) => platformCompaniesApi.updateSubscription(id, data),
    onSuccess: () => {
      notify.success(t('platform.company.planChanged'));
      void qc.invalidateQueries({ queryKey: ['platform', 'companies', id] });
    },
  });
}

export function useUpdateModules(id: string) {
  const { t } = useT();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CompanyModulesUpdateRequest) => platformCompaniesApi.updateModules(id, data),
    onSuccess: () => {
      notify.success(t('platform.company.modulesSaved'));
      void qc.invalidateQueries({ queryKey: ['platform', 'companies', id, 'modules'] });
    },
  });
}

export function useStartSwitch(id: string) {
  const { t } = useT();
  return useMutation({
    mutationFn: (data: PlatformSwitchStartRequest) => platformCompaniesApi.startSwitch(id, data),
    onSuccess: ({ switchCode, targetUrl }) => {
      notify.success(t('platform.company.switchStarted'));
      const url = `${targetUrl}${targetUrl.includes('?') ? '&' : '?'}switchCode=${encodeURIComponent(switchCode)}`;
      const opened = window.open(url, '_blank', 'noopener');
      if (!opened) {
        void navigator.clipboard?.writeText(url);
        notify.info(t('platform.company.switchCopied'));
      }
    },
  });
}

export function useServiceAccounts(params: PageParams = {}) {
  return useQuery({
    queryKey: ['platform', 'service-accounts', params],
    queryFn: () => platformServiceAccountsApi.list(params),
  });
}

export function useCreateServiceAccount() {
  const { t } = useT();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: ServiceAccountCreateRequest) => platformServiceAccountsApi.create(data),
    onSuccess: () => {
      notify.success(t('platform.svc.created'));
      void qc.invalidateQueries({ queryKey: ['platform', 'service-accounts'] });
    },
  });
}

export function useRevokeServiceAccount() {
  const { t } = useT();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => platformServiceAccountsApi.revoke(id),
    onSuccess: () => {
      notify.success(t('platform.svc.revokedOk'));
      void qc.invalidateQueries({ queryKey: ['platform', 'service-accounts'] });
    },
  });
}

export function usePlatformAuditLogs(params: PlatformAuditParams = {}) {
  return useQuery({
    queryKey: ['platform', 'audit-logs', params],
    queryFn: () => platformAuditApi.list(params),
  });
}

// ─── Mail testing (K-51) ───

export function usePlatformMailInfo() {
  return useQuery({
    queryKey: ['platform', 'mail', 'info'],
    queryFn: () => platformMailApi.getInfo(),
  });
}

/** Preview is explicit, sample-data-driven — a mutation, not a cached query. */
export function useMailPreview() {
  return useMutation({
    mutationFn: (data: PlatformMailSampleData) => platformMailApi.preview(data),
  });
}

export function useMailTestSend() {
  const { t } = useT();
  return useMutation({
    mutationFn: (data: PlatformMailTestSendRequest) => platformMailApi.testSend(data),
    onSuccess: ({ channel }) => {
      notify.success(
        channel === 'SMTP'
          ? t('platform.mail.sentSmtp')
          : t('platform.mail.sentLocal', { channel }),
      );
    },
  });
}
