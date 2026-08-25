import { api, normalizePage, searchPost } from '../../lib/api';
import type { FilterCriteria, PageParams, PageResponse } from '../../types';
import type { AuditLog, LoginHistory, RequestLog } from './types';

/** K-49 structured column-filter clauses (shared by the three audit surfaces). */
export interface AuditFilterParams {
  /** Structured column filters — routed through `POST /{resource}/search` when present. */
  filters?: FilterCriteria[];
}

export interface AuditLogParams extends PageParams, AuditFilterParams {
  action?: string;
  actorId?: string;
}

export interface LoginHistoryParams extends PageParams, AuditFilterParams {
  userId?: string;
  success?: boolean;
}

export interface RequestLogParams extends PageParams, AuditFilterParams {
  traceId?: string;
  method?: string;
  status?: number;
  userId?: string;
  username?: string;
}

/**
 * Audit & login-history endpoints (K-19 read side). Both require the
 * {@code iam:audit:read} permission; the backend enforces it, so the SPA merely
 * forwards any filter the page exposes. GET params stay for the first-match
 * filters; structured column filters (K-49) route through the POST /search
 * endpoints with the GET params folded in as explicit EQ clauses.
 */
export const auditLogsApi = {
  list: (params: AuditLogParams = {}) => {
    const { action, actorId, ...rest } = params;
    if (!params.filters?.length) {
      return api.get<PageResponse<AuditLog>>(`/api/v1/audit-logs${buildQuery({ ...rest, action, actorId })}`).then(normalizePage);
    }
    const clauses: FilterCriteria[] = [
      ...(action ? [{ field: 'action', operator: 'EQ' as const, values: [action] }] : []),
      ...(actorId ? [{ field: 'actorId', operator: 'EQ' as const, values: [actorId] }] : []),
    ];
    return searchPost<AuditLog>('/api/v1/audit-logs/search', { ...rest, filters: [...clauses, ...params.filters] });
  },
};

export const loginHistoryApi = {
  list: (params: LoginHistoryParams = {}) => {
    const { userId, success, ...rest } = params;
    if (!params.filters?.length) {
      return api.get<PageResponse<LoginHistory>>(`/api/v1/login-history${buildQuery({ ...rest, userId, success })}`).then(normalizePage);
    }
    const clauses: FilterCriteria[] = [
      ...(userId ? [{ field: 'userId', operator: 'EQ' as const, values: [userId] }] : []),
      ...(success != null ? [{ field: 'success', operator: 'EQ' as const, values: [String(success)] }] : []),
    ];
    return searchPost<LoginHistory>('/api/v1/login-history/search', { ...rest, filters: [...clauses, ...params.filters] });
  },
};

export const requestLogsApi = {
  list: (params: RequestLogParams = {}) => {
    const { traceId, method, status, userId, username, ...rest } = params;
    if (!params.filters?.length) {
      return api.get<PageResponse<RequestLog>>(`/api/v1/request-logs${buildQuery({ ...rest, traceId, method, status, userId, username })}`).then(normalizePage);
    }
    const clauses: FilterCriteria[] = [
      ...(traceId ? [{ field: 'traceId', operator: 'EQ' as const, values: [traceId] }] : []),
      ...(method ? [{ field: 'method', operator: 'EQ' as const, values: [method] }] : []),
      ...(status != null ? [{ field: 'status', operator: 'EQ' as const, values: [String(status)] }] : []),
      ...(userId ? [{ field: 'userId', operator: 'EQ' as const, values: [userId] }] : []),
      ...(username ? [{ field: 'username', operator: 'EQ' as const, values: [username] }] : []),
    ];
    return searchPost<RequestLog>('/api/v1/request-logs/search', { ...rest, filters: [...clauses, ...(params.filters ?? [])] });
  },
};

/**
 * Builds a query string from page/sort plus any present filter values, skipping
 * empties. Unlike the generic {@code toQuery} helper (which only carries page/size/
 * sort), this one threads the audit-specific filters (action/actorId/userId/success);
 * array values (qFields) serialize as repeated params.
 */
function buildQuery(params: object): string {
  const sp = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) {
      (value as unknown[]).forEach((v) => sp.append(key, String(v)));
    } else {
      sp.set(key, String(value));
    }
  }
  const out = sp.toString();
  return out ? `?${out}` : '';
}
