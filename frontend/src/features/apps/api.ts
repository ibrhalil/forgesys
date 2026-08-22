import { api, normalizePage, toQuery } from '../../lib/api';
import type { PageParams, PageResponse } from '../../types';
import type {
  App,
  AppDetail,
  AppProperty,
  AppPropertyRequest,
  AppRecord,
  AppRecordRequest,
  AppRequest,
  AppView,
  AppViewRequest,
} from './types';

export const appsApi = {
  // ─── Apps ───
  list: (params: PageParams = {}) =>
    api.get<PageResponse<App>>(`/api/v1/apps${toQuery(params)}`).then(normalizePage),
  get: (id: string) => api.get<AppDetail>(`/api/v1/apps/${id}`),
  create: (data: AppRequest) => api.post<App>('/api/v1/apps', data),
  update: (id: string, data: AppRequest) => api.put<App>(`/api/v1/apps/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/apps/${id}`),

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
