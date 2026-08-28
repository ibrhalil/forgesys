import { api, normalizePage, searchQueryGet, toQuery } from '../../lib/api';
import type { PageParams, PageResponse, SearchOrListParams } from '../../types';
import type { Role, CreateRoleRequest, AssignPermissionsRequest, AssignRolesRequest } from './types';

export const rolesApi = {
  list: (params: PageParams = {}) =>
    api.get<PageResponse<Role>>(`/api/v1/roles${toQuery(params)}`).then(normalizePage),
  /** K-55 wire-flip: one GET with the encoded `sq` query (over-cap → POST fallback). */
  searchOrList: (params: SearchOrListParams) => searchQueryGet<Role>('/api/v1/roles', params),
  get: (id: string) => api.get<Role>(`/api/v1/roles/${id}`),
  create: (data: CreateRoleRequest) => api.post<Role>('/api/v1/roles', data),
  update: (id: string, data: CreateRoleRequest) => api.put<Role>(`/api/v1/roles/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/roles/${id}`),
  setPermissions: (id: string, data: AssignPermissionsRequest) =>
    api.put<Role>(`/api/v1/roles/${id}/permissions`, data),
  /** Faz 4a: replace the roles this role inherits permissions from (role inheritance). */
  setParents: (id: string, data: AssignRolesRequest) =>
    api.put<Role>(`/api/v1/roles/${id}/parents`, data),
};
