import { api, normalizePage, toQuery } from '../../lib/api';
import type { PageParams, PageResponse } from '../../types';
import type { AssignRolesRequest } from '../roles/types';
import type {
  User, UserDirectoryView, UserActivity,
  CreateUserRequest, UpdateUserRequest, UserProfileUpdateRequest,
  PasswordChangeRequest, AdminPasswordResetRequest, AssignGroupsRequest,
} from './types';

export const usersApi = {
  /** Flat directory projection (DB-side join + counts), visibility-scoped by the backend. */
  list: (params: PageParams = {}) =>
    api.get<PageResponse<UserDirectoryView>>(`/api/v1/users${toQuery(params)}`).then(normalizePage),
  get: (id: string) => api.get<User>(`/api/v1/users/${id}`),
  create: (data: CreateUserRequest) => api.post<User>('/api/v1/users', data),
  update: (id: string, data: UpdateUserRequest) =>
    api.put<User>(`/api/v1/users/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/users/${id}`),
  /** Clears an active brute-force lockout ahead of expiry ([RISK-22] admin unlock, K-37: POST). */
  unlock: (id: string) => api.post<void>(`/api/v1/users/${id}/unlock`),
  setRoles: (id: string, data: AssignRolesRequest) =>
    api.put<User>(`/api/v1/users/${id}/roles`, data),
  setGroups: (id: string, data: AssignGroupsRequest) =>
    api.put<User>(`/api/v1/users/${id}/groups`, data),
  resetPassword: (id: string, data: AdminPasswordResetRequest) =>
    api.patch<void>(`/api/v1/users/${id}/password`, data),
  /** Sorted effective permission names: direct roles + active-group roles + inheritance. */
  effectivePermissions: (id: string) =>
    api.get<string[]>(`/api/v1/users/${id}/effective-permissions`),
  /** Temporal activity summary: creation/update stamps, last login, last failed login. */
  activity: (id: string) => api.get<UserActivity>(`/api/v1/users/${id}/activity`),

  // Self-service (/users/me/*) — no iam permission required.
  // GET /users/me (the single /me) lives in authApi — the session store owns it.
  updateMyProfile: (data: UserProfileUpdateRequest) =>
    api.put<User>('/api/v1/users/me/profile', data),
  changeMyPassword: (data: PasswordChangeRequest) =>
    api.put<void>('/api/v1/users/me/password', data),
};
