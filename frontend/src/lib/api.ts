import type { ApiErrorResponse, PageParams, PageResponse, PageResult } from '../types';
import { useTenantStore } from '../store/tenantStore';

export class ApiError extends Error {
  status: number;
  code: string;
  body: ApiErrorResponse;

  constructor(status: number, code: string, body: ApiErrorResponse) {
    super(body.message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.body = body;
  }
}

const BASE_URL = '';

// --- Transparent refresh-on-401 -------------------------------------------
// The 15-min access token expires while the SPA is open. On a 401 we transparently
// call /api/v1/auth/refresh (the httpOnly sf_refresh_token cookie is sent by the
// browser automatically) and retry the original request once. The refresh token is
// never read by JS (cookie-only). Concurrent 401s coalesce into a single /refresh
// via the shared refreshPromise. Auth endpoints are excluded so a real auth failure
// there is not mistaken for an expirable token.

/** Paths whose 401 is a genuine auth failure (not an expired access token). */
const REFRESH_SKIP_EXACT = new Set([
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
]);
const REFRESH_SKIP_PREFIX = '/api/v1/auth/company/';

function shouldSkipRefresh(path: string): boolean {
  return REFRESH_SKIP_EXACT.has(path) || path.startsWith(REFRESH_SKIP_PREFIX);
}

let refreshPromise: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    try {
      const tenantId = useTenantStore.getState().tenantId;
      const headers: Record<string, string> = {};
      if (tenantId) headers['X-Tenant-ID'] = tenantId;
      const res = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
        headers,
      });
      return res.ok;
    } catch {
      return false;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

/**
 * Invoked when the session can no longer be refreshed (refresh token absent/expired/
 * revoked). Decoupled via a setter so {@link apiFetch} never imports the auth store
 * (avoids a circular dependency: lib/api <- store/authStore <- api/auth <- lib/api).
 * {@link useAuthStore} registers the handler at module load.
 */
let sessionExpiredHandler: (() => void) | null = null;

export function setSessionExpiredHandler(handler: () => void): void {
  sessionExpiredHandler = handler;
}

async function sendRequest(path: string, options: RequestInit): Promise<Response> {
  // Single source of truth: the tenant subdomain lives in the tenant store
  // (which mirrors localStorage + subdomain detection). Sent as X-Tenant-ID
  // so the dev-profile TenantFilter can resolve the tenant schema.
  const tenantId = useTenantStore.getState().tenantId;
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  };
  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }
  return fetch(`${BASE_URL}${path}`, {
    ...options,
    credentials: 'include',
    headers,
  });
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  let response = await sendRequest(path, options);

  // Expired access token: refresh once (shared), then retry the original request.
  if (response.status === 401 && !shouldSkipRefresh(path)) {
    if (await refreshSession()) {
      // Request bodies here are always JSON strings (see api.post/put/patch) — safe
      // to resend on retry; GET/DELETE carry no body.
      response = await sendRequest(path, options);
    } else {
      // Refresh failed: the session is gone. Signal the store so RequireAuth
      // redirects to /login, then surface the original 401 to the caller.
      sessionExpiredHandler?.();
    }
  }

  if (!response.ok) {
    let body: ApiErrorResponse;
    try {
      body = (await response.json()) as ApiErrorResponse;
    } catch {
      body = {
        timestamp: new Date().toISOString(),
        status: response.status,
        error: response.statusText,
        code: 'unknown',
        message: response.statusText,
        path,
        traceId: '',
        fields: [],
      };
    }
    throw new ApiError(response.status, body.code, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

/**
 * Builds a `?page=&size=&sort=&q=` query string from {@link PageParams} (empty string
 * if none). Structured `sorts[]` is serialized as repeated `sort=field,dir` params
 * (Spring Data multi-sort), alongside the legacy raw `sort` string.
 */
export function toQuery(params: PageParams = {}): string {
  const sp = new URLSearchParams();
  if (params.page != null) sp.set('page', String(params.page));
  if (params.size != null) sp.set('size', String(params.size));
  if (params.sort) sp.set('sort', params.sort);
  (params.sorts ?? []).forEach((s) => sp.append('sort', `${s.field},${s.dir}`));
  if (params.q) sp.set('q', params.q);
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}

/**
 * Normalizes a backend {@link PageResponse} into the UI-facing {@link PageResult},
 * reading metadata from the API-owned `data[] + meta` shape, falling back to the
 * legacy Spring Data layouts during rollout.
 */
export function normalizePage<T>(raw: PageResponse<T>): PageResult<T> {
  const meta = raw.meta;
  return {
    items: raw.data ?? raw.content ?? [],
    page: meta?.page ?? raw.number ?? 0,
    size: meta?.pageSize ?? raw.size ?? raw.content?.length ?? 0,
    totalElements: meta?.totalElements ?? raw.totalElements ?? raw.content?.length ?? 0,
    totalPages: meta?.totalPages ?? raw.totalPages ?? (raw.content?.length ? 1 : 0),
    hasNext: meta?.hasNext,
    hasPrevious: meta?.hasPrevious,
  };
}

// Convenience methods
export const api = {
  get: <T>(path: string) => apiFetch<T>(path, { method: 'GET' }),
  post: <T>(path: string, body?: unknown) =>
    apiFetch<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    apiFetch<T>(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body?: unknown) =>
    apiFetch<T>(path, { method: 'PATCH', body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => apiFetch<T>(path, { method: 'DELETE' }),
};
