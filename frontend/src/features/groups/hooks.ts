import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { groupsApi } from './api';
import type { PageParams } from '../../types';
import type { AssignRolesRequest } from '../roles/types';
import type { CreateGroupRequest, AssignMembersRequest } from './types';

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

export function useUpdateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: CreateGroupRequest }) => groupsApi.update(id, data),
    onSuccess: (group) => qc.invalidateQueries({ queryKey: ['groups'] }).then(() => qc.invalidateQueries({ queryKey: ['groups', group.id] })),
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

export function useGroup(id?: string) {
  return useQuery({ queryKey: ['groups', id], queryFn: () => groupsApi.get(id!), enabled: !!id });
}

export function useGroupEffectivePermissions(id?: string) {
  return useQuery({
    queryKey: ['groups', id, 'effective-permissions'],
    queryFn: () => groupsApi.effectivePermissions(id!),
    enabled: !!id,
  });
}
