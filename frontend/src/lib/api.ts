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

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
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

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    credentials: 'include',
    headers,
  });

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

/** Builds a `?page=&size=&sort=` query string from {@link PageParams} (empty string if none). */
export function toQuery(params: PageParams = {}): string {
  const sp = new URLSearchParams();
  if (params.page != null) sp.set('page', String(params.page));
  if (params.size != null) sp.set('size', String(params.size));
  if (params.sort) sp.set('sort', params.sort);
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}

/**
 * Normalizes a Spring Data {@link PageResponse} into the UI-facing {@link PageResult},
 * reading metadata from either the flat layout or the nested {@code page} object.
 */
export function normalizePage<T>(raw: PageResponse<T>): PageResult<T> {
  const meta = raw.page;
  return {
    items: raw.content ?? [],
    page: meta?.number ?? raw.number ?? 0,
    size: meta?.size ?? raw.size ?? raw.content?.length ?? 0,
    totalElements: meta?.totalElements ?? raw.totalElements ?? raw.content?.length ?? 0,
    totalPages: meta?.totalPages ?? raw.totalPages ?? 1,
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
