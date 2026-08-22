import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { appsApi } from './api';
import type { PageParams } from '../../types';
import type { AppPropertyRequest, AppRecordRequest, AppRequest } from './types';
import { ApiError } from '../../lib/api';
import { notify, notifyApiError } from '../../lib/notify';
import { t } from '../../lib/i18n';

/**
 * Plan-limit soft block (403 `app_limit_reached`): backend exposes no limit numbers,
 * so the indicator is reactive — a translated warning toast instead of the generic
 * error one. Mutation-level onError overrides the global default (no double toast).
 */
function toastMutationError(err: unknown): void {
  if (err instanceof ApiError && err.code === 'app_limit_reached') {
    notify.warning(t('apps.limitReached'));
    return;
  }
  notifyApiError(err);
}

// ─── Apps ───
export function useApps(params: PageParams = {}) {
  return useQuery({ queryKey: ['apps', params], queryFn: () => appsApi.list(params) });
}

export function useApp(id?: string) {
  return useQuery({ queryKey: ['apps', id], queryFn: () => appsApi.get(id!), enabled: !!id });
}

export function useCreateApp() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: AppRequest) => appsApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['apps'] }),
    onError: toastMutationError,
  });
}

export function useUpdateApp() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AppRequest }) => appsApi.update(id, data),
    onSuccess: (app) =>
      qc.invalidateQueries({ queryKey: ['apps'] }).then(() => qc.invalidateQueries({ queryKey: ['apps', app.id] })),
  });
}

export function useDeleteApp() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => appsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['apps'] }),
  });
}

// ─── Properties (invalidate the detail — properties live inside it) ───
export function useCreateProperty(appId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: AppPropertyRequest) => appsApi.createProperty(appId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['apps', appId] }),
  });
}

export function useUpdateProperty(appId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ propertyId, data }: { propertyId: string; data: AppPropertyRequest }) =>
      appsApi.updateProperty(appId, propertyId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['apps', appId] }),
  });
}

export function useDeleteProperty(appId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (propertyId: string) => appsApi.deleteProperty(appId, propertyId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['apps', appId] }),
  });
}

// ─── Records ───
export function useRecords(appId: string | undefined, params: PageParams = {}) {
  return useQuery({
    queryKey: ['apps', appId, 'records', params],
    queryFn: () => appsApi.listRecords(appId!, params),
    enabled: !!appId,
  });
}

export function useCreateRecord(appId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: AppRecordRequest) => appsApi.createRecord(appId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['apps', appId, 'records'] }),
    onError: toastMutationError,
  });
}

export function usePatchRecord(appId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ recordId, data }: { recordId: string; data: AppRecordRequest }) =>
      appsApi.patchRecord(appId, recordId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['apps', appId, 'records'] }),
  });
}

export function useDeleteRecord(appId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (recordId: string) => appsApi.deleteRecord(appId, recordId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['apps', appId, 'records'] }),
  });
}
