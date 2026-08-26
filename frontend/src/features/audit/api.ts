import { api, normalizePage, searchPost, toQuery } from '../../lib/api';
import type { FilterCriteria, PageParams, PageResponse, SearchOrListParams } from '../../types';
import type { AuditLog, LoginHistory, RequestLog } from './types';

/** Audit GET lists keep their scoped legacy filters alongside the K-49 structured clauses. */
export type AuditLogParams = SearchOrListParams & { action?: string; actorId?: string };

export type LoginHistoryParams = SearchOrListParams & { userId?: string; success?: boolean };

export type RequestLogParams = SearchOrListParams & {
  traceId?: string;
  method?: string;
  status?: number;
  userId?: string;
  username?: string;
};

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
 * Query string for the audit GET lists: the shared page/sort/q serialization
 * (`toQuery`) plus the audit-specific first-match filters (action/actorId/
 * userId/success/...), skipping empties.
 */
function buildQuery(params: PageParams & Record<string, unknown>): string {
  const { page, size, sort, sorts, q, qFields, ...rest } = params;
  const sp = new URLSearchParams(toQuery({ page, size, sort, sorts, q, qFields }).replace(/^\?/, ''));
  for (const [key, value] of Object.entries(rest)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) {
      value.forEach((v) => sp.append(key, String(v)));
    } else {
      sp.set(key, String(value));
    }
  }
  const out = sp.toString();
  return out ? `?${out}` : '';
}
