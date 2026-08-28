import { keepPreviousData, useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { modulesApi } from './api';

// ─── Modules ───
export function useModules() {
  return useQuery({ queryKey: ['modules'], queryFn: modulesApi.list, placeholderData: keepPreviousData });
}

export function useActivateModule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (key: string) => modulesApi.activate(key),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['modules'] }),
  });
}
