import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { platformApi, setPlatformSessionExpiredHandler } from '../lib/platformApi';
import { useTenantStore } from '../store/tenantStore';

/**
 * K-50 platform client: requests must NEVER carry the tenant X-Tenant-ID header
 * (even when a tenant is active in the store), 401s refresh against the PLATFORM
 * refresh endpoint, and platform-auth-surface 401s are terminal.
 */

const DATA_PATH = '/api/v1/platform/me';
const REFRESH_PATH = '/api/v1/platform/auth/refresh';

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const errorBody = (code: string) => ({
  timestamp: new Date().toISOString(),
  status: 401,
  error: 'Unauthorized',
  code,
  message: 'msg',
  path: DATA_PATH,
  traceId: '',
  fields: [],
});

const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('platformApi', () => {
  beforeEach(() => {
    // An active tenant must not leak into platform requests.
    useTenantStore.setState({ tenantId: 'acme' });
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    useTenantStore.setState({ tenantId: null });
    setPlatformSessionExpiredHandler(() => {});
  });

  it('never sends X-Tenant-ID, even with an active tenant', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      json({ userId: 'u1' }, 200));
    vi.stubGlobal('fetch', fetchMock);

    await platformApi.get(DATA_PATH);

    const headers = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(headers['X-Tenant-ID']).toBeUndefined();
    expect(String(fetchMock.mock.calls[0][0])).toBe(DATA_PATH);
  });

  it('refreshes against the PLATFORM refresh endpoint and retries', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(input);
      if (url === REFRESH_PATH) {
        await tick();
        return json({ accessToken: 'x' }, 200);
      }
      return fetchMock.mock.calls.filter((c) => String(c[0]) === url).length <= 1
        ? json(errorBody('x'), 401)
        : json({ userId: 'u1' }, 200);
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(platformApi.get(DATA_PATH)).resolves.toEqual({ userId: 'u1' });
    const refreshCalls = fetchMock.mock.calls.filter((c) => String(c[0]) === REFRESH_PATH);
    expect(refreshCalls).toHaveLength(1);
    // The refresh call is tenant-less too.
    const refreshHeaders = (refreshCalls[0][1] as RequestInit).headers as Record<string, string>;
    expect(refreshHeaders['X-Tenant-ID']).toBeUndefined();
  });

  it('signals session expiry when the platform refresh fails', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) =>
      String(input) === REFRESH_PATH ? json({}, 401) : json(errorBody('x'), 401));
    vi.stubGlobal('fetch', fetchMock);
    const expired = vi.fn();
    setPlatformSessionExpiredHandler(expired);

    await expect(platformApi.get(DATA_PATH)).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledTimes(1);
  });

  it('treats a platform login 401 as terminal (no refresh)', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      json(errorBody('auth_bad_credentials'), 401));
    vi.stubGlobal('fetch', fetchMock);
    const expired = vi.fn();
    setPlatformSessionExpiredHandler(expired);

    await expect(
      platformApi.post('/api/v1/platform/auth/login', { email: 'a@b.c', password: 'x' }),
    ).rejects.toMatchObject({ name: 'ApiError' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(expired).not.toHaveBeenCalled();
  });
});
