import { keepPreviousData, useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { rolesApi } from './api';
import type { CreateRoleRequest, AssignRolesRequest, AssignPermissionsRequest } from './types';

// ─── Roles ───
export function useRoles(params: Parameters<typeof rolesApi.searchOrList>[0] = {}) {
  return useQuery({ queryKey: ['roles', params], queryFn: () => rolesApi.searchOrList(params), placeholderData: keepPreviousData });
}

export function useRole(id?: string) {
  return useQuery({ queryKey: ['roles', id], queryFn: () => rolesApi.get(id!), enabled: !!id });
}

export function useCreateRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateRoleRequest) => rolesApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }),
  });
}

export function useUpdateRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: CreateRoleRequest }) => rolesApi.update(id, data),
    onSuccess: (role) => qc.invalidateQueries({ queryKey: ['roles'] }).then(() => qc.invalidateQueries({ queryKey: ['roles', role.id] })),
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

/** Faz 4a: replace the parent roles this role inherits permissions from. */
export function useSetRoleParents() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AssignRolesRequest }) =>
      rolesApi.setParents(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }),
  });
}
