import { api, normalizePage } from '../../lib/api';
import type { PageParams, PageResponse } from '../../types';
import type { AuditLog, LoginHistory } from './types';

export interface AuditLogParams extends PageParams {
  action?: string;
  actorId?: string;
}

export interface LoginHistoryParams extends PageParams {
  userId?: string;
  success?: boolean;
}

/**
 * Audit & login-history endpoints (K-19 read side). Both require the
 * {@code iam:audit:read} permission; the backend enforces it, so the SPA merely
 * forwards any filter the page exposes.
 */
export const auditLogsApi = {
  list: (params: AuditLogParams = {}) =>
    api.get<PageResponse<AuditLog>>(`/api/v1/audit-logs${buildQuery(params)}`).then(normalizePage),
};

export const loginHistoryApi = {
  list: (params: LoginHistoryParams = {}) =>
    api.get<PageResponse<LoginHistory>>(`/api/v1/login-history${buildQuery(params)}`).then(normalizePage),
};

/**
 * Builds a query string from page/sort plus any present filter values, skipping
 * empties. Unlike the generic {@code toQuery} helper (which only carries page/size/
 * sort), this one threads the audit-specific filters (action/actorId/userId/success).
 */
function buildQuery(params: object): string {
  const sp = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      sp.set(key, String(value));
    }
  }
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}
