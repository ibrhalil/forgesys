import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { permissionsApi } from './api';
import type { CreatePermissionRequest } from './types';

// ─── Permissions ───
export function usePermissions() {
  return useQuery({ queryKey: ['permissions'], queryFn: permissionsApi.list });
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
