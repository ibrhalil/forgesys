import { api, normalizePage, toQuery } from '../../lib/api';
import type { PageParams, PageResponse } from '../../types';
import type { Role, CreateRoleRequest, AssignPermissionsRequest, AssignRolesRequest } from './types';

export const rolesApi = {
  list: (params: PageParams = {}) =>
    api.get<PageResponse<Role>>(`/api/v1/roles${toQuery(params)}`).then(normalizePage),
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
