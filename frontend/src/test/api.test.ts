import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, setSessionExpiredHandler } from '../lib/api';

/**
 * Unit tests for the transparent refresh-on-401 in `lib/api.ts` (K-39 first tests):
 * concurrent 401s must coalesce into a single `/auth/refresh` call, a failed
 * refresh must signal `sessionExpiredHandler` and surface the original ApiError,
 * and genuine auth-endpoint 401s must never trigger a refresh.
 */

const DATA_PATH = '/api/v1/users';
const REFRESH_PATH = '/api/v1/auth/refresh';

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

describe('apiFetch transparent refresh-on-401', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    setSessionExpiredHandler(() => {});
  });

  it('coalesces concurrent 401s into a single /auth/refresh and retries both', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === REFRESH_PATH) {
        await tick(); // keep the shared refreshPromise in flight while the second 401 queues up
        return json({ accessToken: 'x', refreshToken: 'y', expiresIn: 900 }, 200);
      }
      // The in-progress call is already recorded — the first two data calls 401, retries succeed.
      const dataCalls = fetchMock.mock.calls.filter((c) => String(c[0]) === url).length;
      return dataCalls <= 2 ? json(errorBody('x'), 401) : json({ data: [], meta: undefined }, 200);
    });
    vi.stubGlobal('fetch', fetchMock);

    const [a, b] = await Promise.all([api.get(DATA_PATH), api.get(DATA_PATH)]);

    expect(a).toEqual({ data: [], meta: undefined });
    expect(b).toEqual({ data: [], meta: undefined });
    const calls = (url: string) => fetchMock.mock.calls.filter((c) => String(c[0]) === url);
    expect(calls(REFRESH_PATH)).toHaveLength(1); // single-flight
    expect(calls(DATA_PATH)).toHaveLength(4); // 2 initial 401s + 2 retries
  });

  it('signals sessionExpiredHandler and throws ApiError when the refresh fails', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === REFRESH_PATH) return json({}, 401);
      return json(errorBody('auth_bad_credentials'), 401);
    });
    vi.stubGlobal('fetch', fetchMock);
    const expired = vi.fn();
    setSessionExpiredHandler(expired);

    await expect(api.get(DATA_PATH)).rejects.toMatchObject({
      name: 'ApiError',
      status: 401,
      code: 'auth_bad_credentials',
    });
    expect(expired).toHaveBeenCalledTimes(1);
    // No retry after a failed refresh.
    expect(fetchMock.mock.calls.filter((c) => String(c[0]) === DATA_PATH)).toHaveLength(1);
  });

  it('never refreshes for auth endpoints (genuine 401)', async () => {
    const fetchMock = vi.fn(async () => json(errorBody('auth_bad_credentials'), 401));
    vi.stubGlobal('fetch', fetchMock);
    const expired = vi.fn();
    setSessionExpiredHandler(expired);

    await expect(api.post('/api/v1/auth/login', { email: 'a@b.c', password: 'x' }))
      .rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(1); // only the login call itself
    expect(expired).not.toHaveBeenCalled();
  });
});
