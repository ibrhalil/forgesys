import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { rolesApi, permissionsApi, groupsApi, usersApi } from '../api/rbac';
import type {
  PageParams,
  CreateRoleRequest, CreateGroupRequest, CreateUserRequest,
  UpdateUserRequest, UserProfileUpdateRequest,
  PasswordChangeRequest, AdminPasswordResetRequest,
  AssignPermissionsRequest, AssignRolesRequest,
  AssignGroupsRequest, AssignMembersRequest,
} from '../types';

// ─── Roles ───
export function useRoles(params: PageParams = {}) {
  return useQuery({ queryKey: ['roles', params], queryFn: () => rolesApi.list(params) });
}

export function useCreateRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateRoleRequest) => rolesApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }),
  });
}

export function useDeleteRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => rolesApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }),
  });
}

export function useSetRolePermissions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AssignPermissionsRequest }) =>
      rolesApi.setPermissions(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }),
  });
}

// ─── Permissions ───
export function usePermissions() {
  return useQuery({ queryKey: ['permissions'], queryFn: permissionsApi.list });
}

// ─── Groups ───
export function useGroups(params: PageParams = {}) {
  return useQuery({ queryKey: ['groups', params], queryFn: () => groupsApi.list(params) });
}

export function useCreateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateGroupRequest) => groupsApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['groups'] }),
  });
}

export function useDeleteGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => groupsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['groups'] }),
  });
}

export function useSetGroupRoles() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AssignRolesRequest }) =>
      groupsApi.setRoles(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['groups'] }),
  });
}

export function useSetGroupMembers() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AssignMembersRequest }) =>
      groupsApi.setMembers(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['groups'] }),
  });
}

// ─── Users ───
export function useUsers(params: PageParams = {}) {
  return useQuery({ queryKey: ['users', params], queryFn: () => usersApi.list(params) });
}

export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateUserRequest) => usersApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  });
}

export function useUpdateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateUserRequest }) =>
      usersApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  });
}

export function useDeleteUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => usersApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  });
}

export function useSetUserRoles() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AssignRolesRequest }) =>
      usersApi.setRoles(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  });
}

export function useSetUserGroups() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AssignGroupsRequest }) =>
      usersApi.setGroups(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  });
}

export function useResetPassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AdminPasswordResetRequest }) =>
      usersApi.resetPassword(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  });
}

// ─── Self-service (/users/me/*) ───
export function useMe() {
  return useQuery({ queryKey: ['users', 'me'], queryFn: usersApi.me });
}

export function useUpdateMyProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: UserProfileUpdateRequest) => usersApi.updateMyProfile(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users', 'me'] }),
  });
}

export function useChangeMyPassword() {
  return useMutation({
    mutationFn: (data: PasswordChangeRequest) => usersApi.changeMyPassword(data),
  });
}
