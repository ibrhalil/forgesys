import type { ApiErrorResponse } from '../types';

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

export interface ApiClientOptions {
  /**
   * Extra headers for every request of this client (the tenant client injects
   * {@code X-Tenant-ID}; the platform client has none — the platform surface is
   * tenant-less and the header must never leak into platform requests).
   */
  buildHeaders?: () => Record<string, string>;
  /** Refresh endpoint called once (single-flight) on a 401 before retrying. */
  refreshPath: string;
  /** Paths whose 401 is a genuine auth failure (not an expired access token). */
  shouldSkipRefresh: (path: string) => boolean;
}

export interface ApiClient {
  fetchJson: <T>(path: string, options?: RequestInit) => Promise<T>;
  fetchBlob: (path: string) => Promise<Blob>;
  setSessionExpiredHandler: (handler: (() => void) | null) => void;
}

/**
 * Cookie-based API client factory with transparent refresh-on-401: on a 401
 * (non-auth endpoints) it calls the client's refresh endpoint once (concurrent
 * 401s coalesce via a shared promise — the httpOnly refresh cookie is sent by
 * the browser, never read by JS) and retries the original request. If the
 * refresh fails, the registered session-expired handler runs so the matching
 * auth store can clear the session (auth guard redirects).
 */
export function createApiClient(options: ApiClientOptions): ApiClient {
  let refreshPromise: Promise<boolean> | null = null;
  let sessionExpiredHandler: (() => void) | null = null;

  async function sendRequest(path: string, requestOptions: RequestInit): Promise<Response> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...((requestOptions.headers as Record<string, string>) || {}),
      ...options.buildHeaders?.(),
    };
    return fetch(`${BASE_URL}${path}`, {
      ...requestOptions,
      credentials: 'include',
      headers,
    });
  }

  async function refreshSession(): Promise<boolean> {
    if (refreshPromise) return refreshPromise;
    refreshPromise = (async () => {
      try {
        const res = await fetch(`${BASE_URL}${options.refreshPath}`, {
          method: 'POST',
          credentials: 'include',
          headers: { ...options.buildHeaders?.() },
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

  /** Shared send + refresh-on-401 + non-OK → ApiError (JSON error bodies). */
  async function request(path: string, requestOptions: RequestInit): Promise<Response> {
    let response = await sendRequest(path, requestOptions);

    // Expired access token: refresh once (shared), then retry.
    if (response.status === 401 && !options.shouldSkipRefresh(path)) {
      if (await refreshSession()) {
        // Bodies here are always JSON strings (see the api helpers) — safe to resend.
        response = await sendRequest(path, requestOptions);
      } else {
        // Session is gone — signal the store (auth guard redirects), surface the 401.
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
    return response;
  }

  async function fetchJson<T>(path: string, requestOptions: RequestInit = {}): Promise<T> {
    const response = await request(path, requestOptions);
    if (response.status === 204) {
      return undefined as T;
    }
    return response.json();
  }

  /** Binary GET (file downloads) over the same refresh/error contract. */
  async function fetchBlob(path: string): Promise<Blob> {
    const response = await request(path, { method: 'GET' });
    return response.blob();
  }

  return {
    fetchJson,
    fetchBlob,
    setSessionExpiredHandler: (handler) => {
      sessionExpiredHandler = handler;
    },
  };
}
