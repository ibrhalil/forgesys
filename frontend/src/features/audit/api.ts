import { apiDownload, searchQueryGet, searchQueryGetUrl } from '../../lib/api';
import type { SearchOrListParams } from '../../types';
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
 * forwards any filter the page exposes. K-55 wire-flip: everything travels as the
 * encoded `sq` query — scoped legacy keys fold into EQ clauses inside
 * {@link searchQueryGet}; an over-cap state falls back to `POST /search`.
 */
export const auditLogsApi = {
  list: (params: AuditLogParams = {}) => searchQueryGet<AuditLog>('/api/v1/audit-logs', params),
};

export const loginHistoryApi = {
  list: (params: LoginHistoryParams = {}) => searchQueryGet<LoginHistory>('/api/v1/login-history', params),
};

export const requestLogsApi = {
  /** K-55 wire-flip pilot: one GET with the encoded `sq` query (over-cap → POST fallback). */
  list: (params: RequestLogParams = {}) => searchQueryGet<RequestLog>('/api/v1/request-logs', params),
  /**
   * CSV export of the CURRENT filters (K-55 F5) — same `sq` query, binary download.
   * Rejects when the query exceeds the wire cap (an export must never silently
   * drop filters).
   */
  exportCsv: (params: RequestLogParams = {}) => {
    const url = searchQueryGetUrl('/api/v1/request-logs/export', params);
    if (url === null) {
      return Promise.reject(new Error('search query exceeds the export limit'));
    }
    return apiDownload(url);
  },
};
