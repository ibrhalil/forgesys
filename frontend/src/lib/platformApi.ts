import type { PageResponse, PageResult, SearchRequestBody } from '../types';
import { normalizePage } from './api';
import { ApiError, createApiClient } from './apiClient';

export { ApiError };

/**
 * Platform API client (K-50): cookie-based requests to {@code /api/v1/platform/*}
 * with the platform refresh surface. Deliberately NO {@code X-Tenant-ID} — the
 * platform API is tenant-agnostic and the tenant header must never leak into
 * platform requests (the platform cookies are path-scoped to {@code /api/v1/platform}).
 */
const client = createApiClient({
  refreshPath: '/api/v1/platform/auth/refresh',
  shouldSkipRefresh: (path) =>
    path === '/api/v1/platform/auth/login' ||
    path === '/api/v1/platform/auth/refresh' ||
    path === '/api/v1/platform/auth/logout',
});

/** Platform-session-expired callback — {@link usePlatformAuthStore} registers it at module load. */
export function setPlatformSessionExpiredHandler(handler: () => void): void {
  client.setSessionExpiredHandler(handler);
}

export const platformApi = {
  get: <T>(path: string) => client.fetchJson<T>(path, { method: 'GET' }),
  post: <T>(path: string, body?: unknown) =>
    client.fetchJson<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    client.fetchJson<T>(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body?: unknown) =>
    client.fetchJson<T>(path, { method: 'PATCH', body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => client.fetchJson<T>(path, { method: 'DELETE' }),
};

/** Platform twin of {@link searchPost} — same body shape, platform client. */
export function platformSearchPost<T>(path: string, body: SearchRequestBody): Promise<PageResult<T>> {
  return platformApi.post<PageResponse<T>>(path, body).then(normalizePage);
}
