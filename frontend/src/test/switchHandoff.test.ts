import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { consumeSwitchCode } from '../features/platform/switchHandoff';
import { useAuthStore } from '../store/authStore';

/**
 * K-50 F6 switch handoff: a `?switchCode=` on the tenant URL is stripped from
 * the address BEFORE the exchange (one-time code + StrictMode double effect),
 * exchanged exactly once, and a failed exchange still burns the param.
 */

const ME_PAYLOAD = {
  id: 'u-admin', email: 'admin@acme.dev', username: 'admin', firstName: null, lastName: null,
  phoneNumber: null, address: null, city: null, country: null, zipCode: null,
  enabled: true, emailVerified: true, lockedUntil: null, roles: [], groups: [], authorities: [],
  impersonation: { actorId: 'p-root', actorEmail: 'root@platform.dev' },
};

describe('consumeSwitchCode', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/');
    useAuthStore.setState({ user: null, isAuthenticated: false });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.history.pushState({}, '', '/');
  });

  it('returns false without touching the network when no code is present', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(consumeSwitchCode()).resolves.toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('strips the param first, exchanges once and refreshes /me', async () => {
    const bodies: Array<{ url: string; body?: unknown }> = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      bodies.push({ url, body: init?.body ? JSON.parse(String(init.body)) : undefined });
      const payload = url === '/api/v1/auth/platform-switch'
        ? { accessToken: 'tok', tokenType: 'Bearer', expiresIn: 1800, userId: 'u-admin', email: 'admin@acme.dev' }
        : ME_PAYLOAD;
      return new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.pushState({}, '', '/projects?switchCode=abc&tab=2');

    await expect(consumeSwitchCode()).resolves.toBe(true);

    // Param stripped before the exchange; other params survive.
    expect(window.location.search).toBe('?tab=2');
    const exchange = bodies.filter((b) => b.url === '/api/v1/auth/platform-switch');
    expect(exchange).toHaveLength(1);
    expect(exchange[0].body).toEqual({ code: 'abc' });
    // /me refreshed with impersonation info.
    expect(useAuthStore.getState().user?.impersonation?.actorEmail).toBe('root@platform.dev');

    // Second run (StrictMode): no code left, no further exchange.
    await expect(consumeSwitchCode()).resolves.toBe(false);
    expect(bodies.filter((b) => b.url === '/api/v1/auth/platform-switch')).toHaveLength(1);
  });

  it('burns the param even when the exchange fails', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      timestamp: '', status: 401, error: '', code: 'auth_unauthenticated',
      message: 'invalid', path: '', traceId: '', fields: [],
    }), { status: 401, headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);
    window.history.pushState({}, '', '/?switchCode=burned');

    await expect(consumeSwitchCode()).rejects.toMatchObject({ status: 401 });
    expect(window.location.search).toBe('');
  });
});
