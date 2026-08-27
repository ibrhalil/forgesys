import { api, normalizePage, searchPost, toQuery } from '../../lib/api';
import type { FilterCriteria, PageParams, PageResponse, SearchRequestBody } from '../../types';
import type {
  CustomApp,
  CustomAppDetail,
  CustomAppPlanLimits,
  CustomAppProperty,
  CustomAppPropertyRequest,
  CustomAppRecord,
  CustomAppRecordRequest,
  CustomAppRequest,
  CustomAppView,
  CustomAppViewRequest,
} from './types';

export interface CustomAppListParams extends PageParams {
  /** Cross-container narrowing (flat list) — the project panel also lands here. */
  projectId?: string;
  /** K-49 structured column-filter clauses. */
  filters?: FilterCriteria[];
}

export const customAppsApi = {
  // ─── Apps ───
  list: (params: CustomAppListParams = {}) => {
    const { projectId, ...page } = params;
    const sp = new URLSearchParams(toQuery(page).replace(/^\?/, ''));
    if (projectId) sp.set('projectId', projectId);
    const qs = sp.toString();
    return api
      .get<PageResponse<CustomApp>>(`/api/v1/custom-apps${qs ? `?${qs}` : ''}`)
      .then(normalizePage);
  },
  /**
   * Engine list read: structured column-filter clauses route through
   * `POST /customApps/search` (with the container narrowing folded in as an EQ clause);
   * without clauses everything stays on the plain GET.
   */
  searchOrList: ({ filters, projectId, ...params }: CustomAppListParams = {}) => {
    if (!filters?.length) {
      return customAppsApi.list({ ...params, projectId });
    }
    const clauses: FilterCriteria[] = [...filters];
    if (projectId) {
      clauses.push({ field: 'projectId', operator: 'EQ', values: [projectId] });
    }
    const body: SearchRequestBody = { ...params, filters: clauses };
    return searchPost<CustomApp>('/api/v1/custom-apps/search', body);
  },
  get: (id: string) => api.get<CustomAppDetail>(`/api/v1/custom-apps/${id}`),
  create: (data: CustomAppRequest) => api.post<CustomApp>('/api/v1/custom-apps', data),
  update: (id: string, data: CustomAppRequest) => api.put<CustomApp>(`/api/v1/custom-apps/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/custom-apps/${id}`),
  /** Plan limits for the usage indicators (values from the backend PlanDefinition registry). */
  planLimits: () => api.get<CustomAppPlanLimits>('/api/v1/custom-apps/plan-limits'),

  // ─── Properties (all writes are apps:customapp:write) ───
  createProperty: (customAppId: string, data: CustomAppPropertyRequest) =>
    api.post<CustomAppProperty>(`/api/v1/custom-apps/${customAppId}/properties`, data),
  updateProperty: (customAppId: string, propertyId: string, data: CustomAppPropertyRequest) =>
    api.put<CustomAppProperty>(`/api/v1/custom-apps/${customAppId}/properties/${propertyId}`, data),
  deleteProperty: (customAppId: string, propertyId: string) =>
    api.delete<void>(`/api/v1/custom-apps/${customAppId}/properties/${propertyId}`),

  // ─── Views (writes are apps:customapp:write; list is apps:customapp:read) ───
  listViews: (customAppId: string) => api.get<CustomAppView[]>(`/api/v1/custom-apps/${customAppId}/views`),
  createView: (customAppId: string, data: CustomAppViewRequest) =>
    api.post<CustomAppView>(`/api/v1/custom-apps/${customAppId}/views`, data),
  updateView: (customAppId: string, viewId: string, data: CustomAppViewRequest) =>
    api.put<CustomAppView>(`/api/v1/custom-apps/${customAppId}/views/${viewId}`, data),
  deleteView: (customAppId: string, viewId: string) =>
    api.delete<void>(`/api/v1/custom-apps/${customAppId}/views/${viewId}`),

  // ─── Records ───
  listRecords: (customAppId: string, params: PageParams = {}) =>
    api.get<PageResponse<CustomAppRecord>>(`/api/v1/custom-apps/${customAppId}/records${toQuery(params)}`).then(normalizePage),
  createRecord: (customAppId: string, data: CustomAppRecordRequest) =>
    api.post<CustomAppRecord>(`/api/v1/custom-apps/${customAppId}/records`, data),
  patchRecord: (customAppId: string, recordId: string, data: CustomAppRecordRequest) =>
    api.patch<CustomAppRecord>(`/api/v1/custom-apps/${customAppId}/records/${recordId}`, data),
  deleteRecord: (customAppId: string, recordId: string) =>
    api.delete<void>(`/api/v1/custom-apps/${customAppId}/records/${recordId}`),
};
