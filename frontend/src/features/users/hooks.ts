import { useMemo } from 'react';
import { useQuery, useMutation, useQueryClient, useQueries } from '@tanstack/react-query';
import { usersApi } from './api';
import { useAuthStore } from '../../store/authStore';
import type { AssignRolesRequest } from '../roles/types';
import type {
  CreateUserRequest, UpdateUserRequest, UserProfileUpdateRequest,
  PasswordChangeRequest, AdminPasswordResetRequest, AssignGroupsRequest,
} from './types';

// ─── Users ───
/** Flat directory list items (DB-side join + counts, visibility-scoped). */
export function useUsers(params: Parameters<typeof usersApi.searchOrList>[0] = {}) {
  return useQuery({ queryKey: ['users', params], queryFn: () => usersApi.searchOrList(params) });
}

export function useUser(id?: string) {
  return useQuery({ queryKey: ['users', id], queryFn: () => usersApi.get(id!), enabled: !!id });
}

/**
 * Resolve user ids → emails at any scale: one shared directory page warms the
 * cache first (`['users', params]` — the standard list key), then ids it does
 * not cover fall back to per-id detail queries on the detail-page key
 * (`['users', id]`), so already-visited details cost nothing. Ids that are
 * still pending (or failed/garbage payloads) simply stay out of the Map —
 * callers fall back to a shortened id.
 */
export function useUserLabels(ids: Array<string | null | undefined>): Map<string, string> {
  const { data: page } = useUsers({ page: 0, size: 100, sorts: [{ field: 'email', dir: 'asc' }] });

  const unique = useMemo(
    () => Array.from(new Set(ids.filter((id): id is string => !!id))),
    [ids],
  );
  const onPage = useMemo(() => new Set((page?.items ?? []).map((u) => u.id)), [page]);

  const details = useQueries({
    queries: unique.map((id) => ({
      queryKey: ['users', id],
      queryFn: () => usersApi.get(id),
      // Only miss ids fetch details — and only once the warm page has landed,
      // so on-page ids never trigger extra requests.
      enabled: page !== undefined && !onPage.has(id),
    })),
  });

  return useMemo(() => {
    const m = new Map<string, string>();
    for (const u of page?.items ?? []) m.set(u.id, u.email);
    unique.forEach((_, i) => {
      const u = details[i].data;
      // Garbage-tolerant: only email-bearing payloads count as resolved.
      if (u && typeof u.email === 'string') m.set(u.id, u.email);
    });
    return m;
  }, [page, unique, details]);
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

/** Re-sends the email-verification mail to an unverified user. */
export function useResendVerification() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => usersApi.resendVerification(id),
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
