import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { usersApi } from './api';
import { useAuthStore } from '../../store/authStore';
import type { PageParams } from '../../types';
import type { AssignRolesRequest } from '../roles/types';
import type {
  CreateUserRequest, UpdateUserRequest, UserProfileUpdateRequest,
  PasswordChangeRequest, AdminPasswordResetRequest, AssignGroupsRequest,
} from './types';

// ─── Users ───
/** Flat directory list items (DB-side join + counts, visibility-scoped). */
export function useUsers(params: PageParams = {}) {
  return useQuery({ queryKey: ['users', params], queryFn: () => usersApi.list(params) });
}

export function useUser(id?: string) {
  return useQuery({ queryKey: ['users', id], queryFn: () => usersApi.get(id!), enabled: !!id });
}

export function useUserEffectivePermissions(id?: string) {
  return useQuery({
    queryKey: ['users', id, 'effective-permissions'],
    queryFn: () => usersApi.effectivePermissions(id!),
    enabled: !!id,
  });
}

/** Temporal activity summary for the detail view (mirrors the effective-permissions key pattern). */
export function useUserActivity(id?: string) {
  return useQuery({
    queryKey: ['users', id, 'activity'],
    queryFn: () => usersApi.activity(id!),
    enabled: !!id,
  });
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

/** Admin unlock — clears an active brute-force lockout ahead of expiry. */
export function useUnlockUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => usersApi.unlock(id),
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
// GET /users/me (the single /me, K-37) is owned by authStore.fetchMe — no useMe hook.
export function useUpdateMyProfile() {
  return useMutation({
    mutationFn: (data: UserProfileUpdateRequest) => usersApi.updateMyProfile(data),
    // Refresh the session snapshot (authStore.user mirrors /users/me) so the shell
    // chip / nav re-render with the new name immediately.
    onSuccess: () => useAuthStore.getState().fetchMe(),
  });
}

export function useChangeMyPassword() {
  return useMutation({
    mutationFn: (data: PasswordChangeRequest) => usersApi.changeMyPassword(data),
  });
}
