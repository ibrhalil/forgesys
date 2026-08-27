import { platformApi, platformSearchPost } from '../../lib/platformApi';
import { normalizePage, toQuery } from '../../lib/api';
import type { PageResponse, SearchRequestBody } from '../../types';
import type { PlatformCompany, PlatformCompanyParams } from './types';
import type {
  CompanyModulesUpdateRequest,
  CompanyReportResponse,
  CompanyStatusUpdateRequest,
  PlatformAuditEntry,
  PlatformAuditParams,
  PlatformModule,
  PlatformSwitchStartRequest,
  PlatformSwitchStartResponse,
  ServiceAccount,
  ServiceAccountCreatedResponse,
  ServiceAccountCreateRequest,
  SubscriptionResponse,
  SubscriptionUpdateRequest,
  PlatformMailInfo,
  PlatformMailPreview,
  PlatformMailSampleData,
  PlatformMailTestSendRequest,
  PlatformMailTestSendResponse,
} from './types';

/**
 * Platform console APIs (K-50 F3–F7). All calls go through the platform client —
 * same-origin platform cookies, never the {@code X-Tenant-ID} header. The backend
 * gates everything behind {@code scope == 'platform'}; the SPA gates visibility.
 */
export const platformCompaniesApi = {
  list: (params: PlatformCompanyParams = {}) => {
    const { status, ...rest } = params;
    const qs = toQuery(rest);
    const suffix = qs
      ? `${qs}${status ? `&status=${status}` : ''}`
      : status
        ? `?status=${status}`
        : '';
    return platformApi
      .get<PageResponse<PlatformCompany>>(`/api/v1/platform/companies${suffix}`)
      .then(normalizePage);
  },
  /** Engine list read: clauses route through `POST /companies/search`, plain reads stay on GET. */
  searchOrList: ({ filters, ...params }: PlatformCompanyParams) =>
    filters?.length
      ? platformSearchPost<PlatformCompany>('/api/v1/platform/companies/search', params)
      : platformCompaniesApi.list(params),
  get: (id: string) => platformApi.get<PlatformCompany>(`/api/v1/platform/companies/${id}`),
  updateStatus: (id: string, data: CompanyStatusUpdateRequest) =>
    platformApi.patch<PlatformCompany>(`/api/v1/platform/companies/${id}/status`, data),
  getSubscription: (id: string) =>
    platformApi.get<SubscriptionResponse>(`/api/v1/platform/companies/${id}/subscription`),
  updateSubscription: (id: string, data: SubscriptionUpdateRequest) =>
    platformApi.put<SubscriptionResponse>(`/api/v1/platform/companies/${id}/subscription`, data),
  getModules: (id: string) =>
    platformApi.get<PlatformModule[]>(`/api/v1/platform/companies/${id}/modules`),
  updateModules: (id: string, data: CompanyModulesUpdateRequest) =>
    platformApi.put<PlatformModule[]>(`/api/v1/platform/companies/${id}/modules`, data),
  getReport: (id: string) =>
    platformApi.get<CompanyReportResponse>(`/api/v1/platform/companies/${id}/report`),
  startSwitch: (id: string, data: PlatformSwitchStartRequest) =>
    platformApi.post<PlatformSwitchStartResponse>(`/api/v1/platform/companies/${id}/switch`, data),
};

export const platformServiceAccountsApi = {
  list: (params: SearchRequestBody = {}) =>
    platformApi
      .get<PageResponse<ServiceAccount>>(`/api/v1/platform/service-accounts${toQuery(params)}`)
      .then(normalizePage),
  create: (data: ServiceAccountCreateRequest) =>
    platformApi.post<ServiceAccountCreatedResponse>('/api/v1/platform/service-accounts', data),
  revoke: (id: string) => platformApi.delete<void>(`/api/v1/platform/service-accounts/${id}`),
};

/** v1 audit read stays on GET with first-match params (no POST search endpoint). */
export const platformAuditApi = {
  list: (params: PlatformAuditParams = {}) => {
    const { action, actorId, targetType, fromDate, toDate, ...rest } = params;
    const sp = new URLSearchParams(toQuery(rest).replace(/^\?/, ''));
    if (action) sp.set('action', action);
    if (actorId) sp.set('actorId', actorId);
    if (targetType) sp.set('targetType', targetType);
    if (fromDate) sp.set('fromDate', fromDate);
    if (toDate) sp.set('toDate', toDate);
    const qs = sp.toString();
    return platformApi
      .get<PageResponse<PlatformAuditEntry>>(`/api/v1/platform/audit-logs${qs ? `?${qs}` : ''}`)
      .then(normalizePage);
  },
};

/** K-51 mail testing: info / no-send preview / test send (platform cookies, no X-Tenant-ID). */
export const platformMailApi = {
  getInfo: () => platformApi.get<PlatformMailInfo>('/api/v1/platform/mail/info'),
  preview: (data: PlatformMailSampleData) =>
    platformApi.post<PlatformMailPreview>('/api/v1/platform/mail/preview', data),
  testSend: (data: PlatformMailTestSendRequest) =>
    platformApi.post<PlatformMailTestSendResponse>('/api/v1/platform/mail/test-send', data),
};
