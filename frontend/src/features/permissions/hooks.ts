import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { permissionsApi, type PermissionListParams } from './api';
import type { CreatePermissionRequest } from './types';

// ─── Permissions ───
/** Full catalog in one response — assign modals and small bounded pickers. */
export function usePermissions() {
  return useQuery({ queryKey: ['permissions'], queryFn: permissionsApi.list });
}

/** Server-side paged read for the permissions list page (K-49). */
export function usePermissionSearch(params: PermissionListParams = {}) {
  return useQuery({
    queryKey: ['permissions', 'search', params],
    queryFn: () => permissionsApi.searchOrList(params),
  });
}

export function usePermission(id?: string) {
  return useQuery({ queryKey: ['permissions', id], queryFn: () => permissionsApi.get(id!), enabled: !!id });
}

export function useCreatePermission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreatePermissionRequest) => permissionsApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['permissions'] }),
  });
}

export function useUpdatePermission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: CreatePermissionRequest }) =>
      permissionsApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['permissions'] }),
  });
}

export function useDeletePermission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => permissionsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['permissions'] }),
  });
}
