import { api, normalizePage, searchQueryGet, toQuery } from '../../lib/api';
import type { FilterCriteria, PageParams, PageResponse } from '../../types';
import type { CreatePermissionRequest, Permission } from './types';

export interface PermissionListParams extends PageParams {
  /** K-49 structured column-filter clauses. */
  filters?: FilterCriteria[];
}

export const permissionsApi = {
  /**
   * Paged catalog (K-37). The page filters/sorts locally — one large page keeps that
   * UX unchanged (backend hard cap: 1000, `spring.data.web.pageable.max-page-size`).
   */
  list: () =>
    api
      .get<PageResponse<Permission>>(`/api/v1/permissions${toQuery({ size: 1000, sort: 'name' })}`)
      .then(normalizePage),
  /** K-55 wire-flip: one GET with the encoded `sq` query (over-cap → POST fallback). */
  searchOrList: (params: PermissionListParams = {}) =>
    searchQueryGet<Permission>('/api/v1/permissions', params),
  get: (id: string) => api.get<Permission>(`/api/v1/permissions/${id}`),
  create: (data: CreatePermissionRequest) => api.post<Permission>('/api/v1/permissions', data),
  update: (id: string, data: CreatePermissionRequest) =>
    api.put<Permission>(`/api/v1/permissions/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/permissions/${id}`),
};
