import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { customAppsApi, type CustomAppListParams } from './api';
import type { PageParams } from '../../types';
import type { CustomAppPropertyRequest, CustomAppRecordRequest, CustomAppRequest, CustomAppViewRequest } from './types';
import { ApiError } from '../../lib/api';
import { notify, notifyApiError } from '../../lib/notify';
import { t } from '../../lib/i18n';

/**
 * Plan-limit soft block (403 `custom_app_limit_reached`): backend exposes no limit numbers,
 * so the indicator is reactive — a translated warning toast instead of the generic
 * error one. Mutation-level onError overrides the global default (no double toast).
 */
function toastMutationError(err: unknown): void {
  if (err instanceof ApiError && err.code === 'custom_app_limit_reached') {
    notify.warning(t('customApps.limitReached'));
    return;
  }
  notifyApiError(err);
}

// ─── Apps ───
export function useCustomApps(params: CustomAppListParams = {}) {
  return useQuery({ queryKey: ['customApps', params], queryFn: () => customAppsApi.searchOrList(params) });
}

export function useCustomApp(id?: string) {
  return useQuery({ queryKey: ['customApps', id], queryFn: () => customAppsApi.get(id!), enabled: !!id });
}

export function useCreateCustomApp() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CustomAppRequest) => customAppsApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps'] }),
    onError: toastMutationError,
  });
}

export function useUpdateCustomApp() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: CustomAppRequest }) => customAppsApi.update(id, data),
    onSuccess: (customApp) =>
      qc.invalidateQueries({ queryKey: ['customApps'] }).then(() => qc.invalidateQueries({ queryKey: ['customApps', customApp.id] })),
  });
}

export function useDeleteCustomApp() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => customAppsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps'] }),
  });
}

// ─── Properties (invalidate the detail — properties live inside it) ───
export function useCreateProperty(customAppId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CustomAppPropertyRequest) => customAppsApi.createProperty(customAppId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps', customAppId] }),
  });
}

export function useUpdateProperty(customAppId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ propertyId, data }: { propertyId: string; data: CustomAppPropertyRequest }) =>
      customAppsApi.updateProperty(customAppId, propertyId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps', customAppId] }),
  });
}

export function useDeleteProperty(customAppId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (propertyId: string) => customAppsApi.deleteProperty(customAppId, propertyId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps', customAppId] }),
  });
}

// ─── Views (invalidate the detail — views live inside it) ───
export function useCreateView(customAppId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CustomAppViewRequest) => customAppsApi.createView(customAppId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps', customAppId] }),
  });
}

export function useUpdateView(customAppId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ viewId, data }: { viewId: string; data: CustomAppViewRequest }) =>
      customAppsApi.updateView(customAppId, viewId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps', customAppId] }),
  });
}

export function useDeleteView(customAppId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (viewId: string) => customAppsApi.deleteView(customAppId, viewId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps', customAppId] }),
  });
}

// ─── Records ───
export function useRecords(customAppId: string | undefined, params: PageParams = {}) {
  return useQuery({
    queryKey: ['customApps', customAppId, 'records', params],
    queryFn: () => customAppsApi.listRecords(customAppId!, params),
    enabled: !!customAppId,
  });
}

/**
 * Single-page fetch for the client-side view renderers (BOARD/CALENDAR/LIST/GALLERY
 * and filtered TABLE) — the TaskBoard precedent: one bounded page, grouped/filtered
 * locally. Cap mirrors the backend max-page-size (1000) which also covers the FREE
 * plan's per-customApp record limit.
 */
export const VIEW_RECORDS_PARAMS: PageParams = {
  page: 0,
  size: 1000,
  sorts: [{ field: 'createdDate', dir: 'desc' }],
};

export function useViewRecords(customAppId: string | undefined) {
  return useRecords(customAppId, VIEW_RECORDS_PARAMS);
}

/**
 * Plan limits for the usage indicators (GET /customApps/plan-limits). Sits under the
 * ['customApps'] prefix, so customApp create/delete mutations refresh it for free. The
 * indicator renders only when this resolves — a failing query simply hides it.
 */
export function usePlanLimits() {
  return useQuery({ queryKey: ['customApps', 'plan-limits'], queryFn: () => customAppsApi.planLimits() });
}

export function useCreateRecord(customAppId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CustomAppRecordRequest) => customAppsApi.createRecord(customAppId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps', customAppId, 'records'] }),
    onError: toastMutationError,
  });
}

export function usePatchRecord(customAppId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ recordId, data }: { recordId: string; data: CustomAppRecordRequest }) =>
      customAppsApi.patchRecord(customAppId, recordId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps', customAppId, 'records'] }),
  });
}

export function useDeleteRecord(customAppId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (recordId: string) => customAppsApi.deleteRecord(customAppId, recordId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customApps', customAppId, 'records'] }),
  });
}
