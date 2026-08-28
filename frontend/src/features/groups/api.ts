import { api, normalizePage, searchQueryGet, toQuery } from '../../lib/api';
import type { PageParams, PageResponse, SearchOrListParams } from '../../types';
import type { AssignRolesRequest } from '../roles/types';
import type { Group, CreateGroupRequest, AssignMembersRequest } from './types';

export const groupsApi = {
  list: (params: PageParams = {}) =>
    api.get<PageResponse<Group>>(`/api/v1/groups${toQuery(params)}`).then(normalizePage),
  /** K-55 wire-flip: one GET with the encoded `sq` query (over-cap → POST fallback). */
  searchOrList: (params: SearchOrListParams) => searchQueryGet<Group>('/api/v1/groups', params),
  get: (id: string) => api.get<Group>(`/api/v1/groups/${id}`),
  create: (data: CreateGroupRequest) => api.post<Group>('/api/v1/groups', data),
  update: (id: string, data: CreateGroupRequest) => api.put<Group>(`/api/v1/groups/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/groups/${id}`),
  setRoles: (id: string, data: AssignRolesRequest) =>
    api.put<Group>(`/api/v1/groups/${id}/roles`, data),
  setMembers: (id: string, data: AssignMembersRequest) =>
    api.put<Group>(`/api/v1/groups/${id}/members`, data),
  /** Sorted effective permission names this group grants its members. */
  effectivePermissions: (id: string) =>
    api.get<string[]>(`/api/v1/groups/${id}/effective-permissions`),
};
