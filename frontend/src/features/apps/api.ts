import { api, normalizePage, searchPost, toQuery } from '../../lib/api';
import type { FilterCriteria, PageParams, PageResponse, SearchRequestBody } from '../../types';
import type {
  App,
  AppDetail,
  AppPlanLimits,
  AppProperty,
  AppPropertyRequest,
  AppRecord,
  AppRecordRequest,
  AppRequest,
  AppView,
  AppViewRequest,
} from './types';

export interface AppListParams extends PageParams {
  /** Cross-container narrowing (flat list) — the project panel also lands here. */
  projectId?: string;
  /** K-49 structured column-filter clauses. */
  filters?: FilterCriteria[];
}

export const appsApi = {
  // ─── Apps ───
  list: (params: AppListParams = {}) => {
    const { projectId, ...page } = params;
    const sp = new URLSearchParams(toQuery(page).replace(/^\?/, ''));
    if (projectId) sp.set('projectId', projectId);
    const qs = sp.toString();
    return api
      .get<PageResponse<App>>(`/api/v1/apps${qs ? `?${qs}` : ''}`)
      .then(normalizePage);
  },
  /**
   * Engine list read: structured column-filter clauses route through
   * `POST /apps/search` (with the container narrowing folded in as an EQ clause);
   * without clauses everything stays on the plain GET.
   */
  searchOrList: ({ filters, projectId, ...params }: AppListParams = {}) => {
    if (!filters?.length) {
      return appsApi.list({ ...params, projectId });
    }
    const clauses: FilterCriteria[] = [...filters];
    if (projectId) {
      clauses.push({ field: 'projectId', operator: 'EQ', values: [projectId] });
    }
    const body: SearchRequestBody = { ...params, filters: clauses };
    return searchPost<App>('/api/v1/apps/search', body);
  },
  get: (id: string) => api.get<AppDetail>(`/api/v1/apps/${id}`),
  create: (data: AppRequest) => api.post<App>('/api/v1/apps', data),
  update: (id: string, data: AppRequest) => api.put<App>(`/api/v1/apps/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/apps/${id}`),
  /** Plan limits for the usage indicators (values from the backend PlanDefinition registry). */
  planLimits: () => api.get<AppPlanLimits>('/api/v1/apps/plan-limits'),

  // ─── Properties (all writes are apps:app:write) ───
  createProperty: (appId: string, data: AppPropertyRequest) =>
    api.post<AppProperty>(`/api/v1/apps/${appId}/properties`, data),
  updateProperty: (appId: string, propertyId: string, data: AppPropertyRequest) =>
    api.put<AppProperty>(`/api/v1/apps/${appId}/properties/${propertyId}`, data),
  deleteProperty: (appId: string, propertyId: string) =>
    api.delete<void>(`/api/v1/apps/${appId}/properties/${propertyId}`),

  // ─── Views (writes are apps:app:write; list is apps:app:read) ───
  listViews: (appId: string) => api.get<AppView[]>(`/api/v1/apps/${appId}/views`),
  createView: (appId: string, data: AppViewRequest) =>
    api.post<AppView>(`/api/v1/apps/${appId}/views`, data),
  updateView: (appId: string, viewId: string, data: AppViewRequest) =>
    api.put<AppView>(`/api/v1/apps/${appId}/views/${viewId}`, data),
  deleteView: (appId: string, viewId: string) =>
    api.delete<void>(`/api/v1/apps/${appId}/views/${viewId}`),

  // ─── Records ───
  listRecords: (appId: string, params: PageParams = {}) =>
    api.get<PageResponse<AppRecord>>(`/api/v1/apps/${appId}/records${toQuery(params)}`).then(normalizePage),
  createRecord: (appId: string, data: AppRecordRequest) =>
    api.post<AppRecord>(`/api/v1/apps/${appId}/records`, data),
  patchRecord: (appId: string, recordId: string, data: AppRecordRequest) =>
    api.patch<AppRecord>(`/api/v1/apps/${appId}/records/${recordId}`, data),
  deleteRecord: (appId: string, recordId: string) =>
    api.delete<void>(`/api/v1/apps/${appId}/records/${recordId}`),
};
