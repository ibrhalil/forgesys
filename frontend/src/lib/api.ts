import type {
  PageParams,
  PageResponse,
  PageResult,
  SearchRequestBody,
} from '../types';
import { useTenantStore } from '../store/tenantStore';
import { ApiError, createApiClient } from './apiClient';

export { ApiError };

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

// Tenant client: every request carries X-Tenant-ID from the tenant store — the
// dev-profile TenantFilter resolves the schema.
const client = createApiClient({
  buildHeaders: (): Record<string, string> => {
    const tenantId = useTenantStore.getState().tenantId;
    return tenantId ? { 'X-Tenant-ID': tenantId } : {};
  },
  refreshPath: '/api/v1/auth/refresh',
  shouldSkipRefresh,
});

/**
 * Session-expired callback, injected via setter so this module never imports the
 * auth store (avoids the circular dep lib/api <- authStore <- api/auth <- lib/api);
 * {@link useAuthStore} registers the handler at module load.
 */
export function setSessionExpiredHandler(handler: () => void): void {
  client.setSessionExpiredHandler(handler);
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  return client.fetchJson<T>(path, options);
}

/**
 * Builds a `?page=&size=&sort=&q=&qFields=` query string from {@link PageParams}
 * (empty string if none). `sorts[]` serializes as repeated `sort=field,dir` params
 * (Spring Data multi-sort); `qFields[]` as repeated params (smart-search targeting).
 */
export function toQuery(params: PageParams = {}): string {
  const sp = new URLSearchParams();
  if (params.page != null) sp.set('page', String(params.page));
  if (params.size != null) sp.set('size', String(params.size));
  if (params.sort) sp.set('sort', params.sort);
  (params.sorts ?? []).forEach((s) => sp.append('sort', `${s.field},${s.dir}`));
  if (params.q) sp.set('q', params.q);
  (params.qFields ?? []).forEach((f) => sp.append('qFields', f));
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}

/** Normalizes a backend {@link PageResponse} into {@link PageResult} — API `data[] + meta` shape with legacy Spring Data fallbacks. */
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

/** `POST /{resource}/search` helper (K-49 filter engine) — sends the body, normalizes the page. */
export function searchPost<T>(path: string, body: SearchRequestBody): Promise<PageResult<T>> {
  return api.post<PageResponse<T>>(path, body).then(normalizePage);
}
