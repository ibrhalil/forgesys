import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { platformApi } from '../api/platform';
import type { CompanyStatusUpdateRequest } from '../types';

export function useCompanies() {
  return useQuery({ queryKey: ['companies'], queryFn: platformApi.listCompanies });
}

export function useUpdateCompanyStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: CompanyStatusUpdateRequest }) =>
      platformApi.updateCompanyStatus(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['companies'] }),
  });
}
