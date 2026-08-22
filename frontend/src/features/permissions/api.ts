import { api, normalizePage, toQuery } from '../../lib/api';
import type { PageResponse } from '../../types';
import type { CreatePermissionRequest, Permission } from './types';

export const permissionsApi = {
  /**
   * Paged catalog (K-37). The page filters/sorts locally — one large page keeps that
   * UX unchanged (backend hard cap: 1000, `spring.data.web.pageable.max-page-size`).
   */
  list: () =>
    api
      .get<PageResponse<Permission>>(`/api/v1/permissions${toQuery({ size: 1000, sort: 'name' })}`)
      .then(normalizePage),
  get: (id: string) => api.get<Permission>(`/api/v1/permissions/${id}`),
  create: (data: CreatePermissionRequest) => api.post<Permission>('/api/v1/permissions', data),
  update: (id: string, data: CreatePermissionRequest) =>
    api.put<Permission>(`/api/v1/permissions/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/permissions/${id}`),
};
