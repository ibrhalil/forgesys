import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, searchPost, searchQueryGet, searchQueryGetUrl, setSessionExpiredHandler, toQuery } from '../lib/api';
import { decodeSearchQuery } from '../lib/searchQuery';
import { flatParams } from './sqUrl';

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

describe('toQuery (K-49 smart-search targeting)', () => {
  it('serializes qFields as repeated params alongside page/sort/q', () => {
    const qs = toQuery({
      page: 0,
      size: 20,
      sorts: [{ field: 'email', direction: 'asc' }],
      q: 'ali',
      qFields: ['firstName', 'email'],
    });
    const sp = new URLSearchParams(qs);
    expect(sp.get('q')).toBe('ali');
    expect(sp.getAll('qFields')).toEqual(['firstName', 'email']);
    expect(sp.getAll('sort')).toEqual(['email,asc']);
  });

  it('omits empty qFields', () => {
    const qs = toQuery({ q: 'x', qFields: [] });
    expect(qs).toBe('?q=x');
  });
});

describe('searchPost (filter-engine helper)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    setSessionExpiredHandler(() => {});
  });

  it('posts the SearchRequestBody and normalizes the PageResponse', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      expect(init?.method).toBe('POST');
      const body = JSON.parse(String(init?.body));
      expect(body.filters).toEqual([{ field: 'enabled', operator: 'EQ', values: ['true'] }]);
      expect(body.qFields).toEqual(['firstName']);
      expect(body.sorts).toEqual([{ field: 'email', direction: 'asc' }]);
      return json(
        {
          data: [{ id: 'u1' }],
          meta: { page: 0, pageSize: 10, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false },
        },
        200,
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await searchPost<{ id: string }>('/api/v1/users/search', {
      page: 0,
      size: 10,
      sorts: [{ field: 'email', direction: 'asc' }],
      filters: [{ field: 'enabled', operator: 'EQ', values: ['true'] }],
      q: 'ali',
      qFields: ['firstName'],
    });

    expect(result.items).toEqual([{ id: 'u1' }]);
    expect(result.totalElements).toBe(1);
    expect(result.page).toBe(0);
  });

  it('locks the POST body sort wire shape: {field, direction} (the dir/direction bug class)', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const body = JSON.parse(String(init?.body));
      expect(body.sorts).toEqual([{ field: 'path', direction: 'desc' }]);
      return json({ data: [], meta: undefined }, 200);
    });
    vi.stubGlobal('fetch', fetchMock);

    await searchPost('/api/v1/request-logs/search', {
      sorts: [{ field: 'path', direction: 'desc' }],
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('searchQueryGet (K-55 wire: flat paging/sort + sq filter blob)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    setSessionExpiredHandler(() => {});
  });

  it('sends paging/sort as flat params and q/filters inside the sq blob', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL) => json({ data: [], meta: undefined }, 200));
    vi.stubGlobal('fetch', fetchMock);

    await searchQueryGet('/api/v1/request-logs', {
      page: 2,
      size: 25,
      sorts: [{ field: 'path', direction: 'desc' }, { field: 'method', direction: 'asc' }],
      q: 'ali',
      filters: [{ field: 'status', operator: 'GTE', values: ['400'] }],
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0][0]);
    const [path, qs] = url.split('?');
    expect(path).toBe('/api/v1/request-logs');
    const sp = new URLSearchParams(qs);
    expect(sp.get('page')).toBe('2');
    expect(sp.get('size')).toBe('25');
    expect(sp.getAll('sort')).toEqual(['path,desc', 'method,asc']);
    expect(sp.has('q')).toBe(false); // q rides the blob
    const sq = decodeSearchQuery(sp.get('sq') ?? '')!;
    expect(sq.q).toBe('ali');
    expect(sq.filters).toEqual([{ field: 'status', operator: 'GTE', values: ['400'] }]);
      });

  it('folds feature-scoped legacy keys into EQ clauses inside the blob', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL) => json({ data: [], meta: undefined }, 200));
    vi.stubGlobal('fetch', fetchMock);

    await searchQueryGet('/api/v1/login-history', { userId: 'u-42', success: true });

    const sp = new URLSearchParams(String(fetchMock.mock.calls[0][0]).split('?')[1]);
    const sq = decodeSearchQuery(sp.get('sq') ?? '')!;
    expect(sq.filters).toEqual([
      { field: 'userId', operator: 'EQ', values: ['u-42'] },
      { field: 'success', operator: 'EQ', values: ['true'] },
    ]);
  });

  it('writes no sq param for a clean query (no q/filters)', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL) => json({ data: [], meta: undefined }, 200));
    vi.stubGlobal('fetch', fetchMock);

    await searchQueryGet('/api/v1/users', { page: 0, size: 20, sorts: [{ field: 'email', direction: 'asc' }] });

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).not.toContain('sq=');
    expect(flatParams(url).getAll('sort')).toEqual(['email,asc']);
  });

  it('falls back to POST /search through the SAME client when over cap (platform fix)', async () => {
    const get = vi.fn(async () => {
      throw new Error('GET must not be called');
    });
    const post = vi.fn(async (_path: string, _body?: unknown) => json({ data: [], meta: undefined }, 200));
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const platformClient = { get, post } as any;

    await searchQueryGet('/api/v1/platform/companies', {
      page: 0,
      size: 20,
      filters: Array.from({ length: 300 }, (_, i) => ({
        field: `field${i}`,
        operator: 'CONTAINS' as const,
        values: ['x'.repeat(100)],
      })),
    }, platformClient);

    expect(get).not.toHaveBeenCalled();
    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0][0]).toBe('/api/v1/platform/companies/search');
    expect((post.mock.calls[0][1] as { filters: unknown[] }).filters).toHaveLength(300);
  });

  it('searchQueryGetUrl returns the combined URL and null when over cap', () => {
    const url = searchQueryGetUrl('/api/v1/request-logs', {
      page: 1,
      size: 10,
      sorts: [{ field: 'path', direction: 'asc' }],
      q: 'x',
    })!;
    expect(url).toContain('page=1');
    expect(flatParams(url).getAll('sort')).toEqual(['path,asc']);
    expect(url).toContain('sq=');

    const over = searchQueryGetUrl('/api/v1/request-logs', {
      q: 'x',
      filters: Array.from({ length: 300 }, (_, i) => ({
        field: `field${i}`,
        operator: 'CONTAINS' as const,
        values: ['x'.repeat(100)],
      })),
    });
    expect(over).toBeNull();
  });
});
