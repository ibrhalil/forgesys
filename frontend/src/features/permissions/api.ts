import { api, normalizePage, searchPost, toQuery } from '../../lib/api';
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
  /**
   * Engine-backed server-side read for the permissions page (K-49): plain params go
   * over GET (`?q=&qFields=&sort=`), structured clauses route through
   * `POST /permissions/search`.
   */
  searchOrList: ({ filters, ...params }: PermissionListParams = {}) => {
    if (!filters?.length) {
      return api
        .get<PageResponse<Permission>>(`/api/v1/permissions${toQuery(params)}`)
        .then(normalizePage);
    }
    return searchPost<Permission>('/api/v1/permissions/search', params);
  },
  get: (id: string) => api.get<Permission>(`/api/v1/permissions/${id}`),
  create: (data: CreatePermissionRequest) => api.post<Permission>('/api/v1/permissions', data),
  update: (id: string, data: CreatePermissionRequest) =>
    api.put<Permission>(`/api/v1/permissions/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/permissions/${id}`),
};
