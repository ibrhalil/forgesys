import { api, normalizePage, toQuery } from '../lib/api';
import type {
  Role, Permission, Group, User,
  PageParams, PageResponse,
  CreateRoleRequest, CreateGroupRequest, CreateUserRequest,
  UpdateUserRequest, UserProfileUpdateRequest,
  PasswordChangeRequest, AdminPasswordResetRequest,
  AssignPermissionsRequest, AssignRolesRequest,
  AssignGroupsRequest, AssignMembersRequest,
} from '../types';

export const rolesApi = {
  list: (params: PageParams = {}) =>
    api.get<PageResponse<Role>>(`/api/v1/roles${toQuery(params)}`).then(normalizePage),
  get: (id: string) => api.get<Role>(`/api/v1/roles/${id}`),
  create: (data: CreateRoleRequest) => api.post<Role>('/api/v1/roles', data),
  delete: (id: string) => api.delete<void>(`/api/v1/roles/${id}`),
  setPermissions: (id: string, data: AssignPermissionsRequest) =>
    api.put<Role>(`/api/v1/roles/${id}/permissions`, data),
};

export const permissionsApi = {
  list: () => api.get<Permission[]>('/api/v1/permissions'),
};

export const groupsApi = {
  list: (params: PageParams = {}) =>
    api.get<PageResponse<Group>>(`/api/v1/groups${toQuery(params)}`).then(normalizePage),
  get: (id: string) => api.get<Group>(`/api/v1/groups/${id}`),
  create: (data: CreateGroupRequest) => api.post<Group>('/api/v1/groups', data),
  delete: (id: string) => api.delete<void>(`/api/v1/groups/${id}`),
  setRoles: (id: string, data: AssignRolesRequest) =>
    api.put<Group>(`/api/v1/groups/${id}/roles`, data),
  setMembers: (id: string, data: AssignMembersRequest) =>
    api.put<Group>(`/api/v1/groups/${id}/members`, data),
};

export const usersApi = {
  list: (params: PageParams = {}) =>
    api.get<PageResponse<User>>(`/api/v1/users${toQuery(params)}`).then(normalizePage),
  get: (id: string) => api.get<User>(`/api/v1/users/${id}`),
  create: (data: CreateUserRequest) => api.post<User>('/api/v1/users', data),
  update: (id: string, data: UpdateUserRequest) =>
    api.put<User>(`/api/v1/users/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/users/${id}`),
  setRoles: (id: string, data: AssignRolesRequest) =>
    api.put<User>(`/api/v1/users/${id}/roles`, data),
  setGroups: (id: string, data: AssignGroupsRequest) =>
    api.put<User>(`/api/v1/users/${id}/groups`, data),
  resetPassword: (id: string, data: AdminPasswordResetRequest) =>
    api.patch<void>(`/api/v1/users/${id}/password`, data),

  // Self-service (/users/me/*) — no iam permission required
  me: () => api.get<User>('/api/v1/users/me'),
  updateMyProfile: (data: UserProfileUpdateRequest) =>
    api.put<User>('/api/v1/users/me/profile', data),
  changeMyPassword: (data: PasswordChangeRequest) =>
    api.put<void>('/api/v1/users/me/password', data),
};
