import { api } from '../../lib/api';
import type { Permission, CreatePermissionRequest } from './types';

export const permissionsApi = {
  list: () => api.get<Permission[]>('/api/v1/permissions'),
  get: (id: string) => api.get<Permission>(`/api/v1/permissions/${id}`),
  create: (data: CreatePermissionRequest) => api.post<Permission>('/api/v1/permissions', data),
  update: (id: string, data: CreatePermissionRequest) =>
    api.put<Permission>(`/api/v1/permissions/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/permissions/${id}`),
};
