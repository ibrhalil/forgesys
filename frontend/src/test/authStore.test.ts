import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';
import { notify } from '../lib/notify';

/**
 * Store-level tests for `authStore.login`: a 200 login whose follow-up /me cannot
 * establish the session (e.g. the access token being rejected) must surface as a
 * visible failure instead of silently reporting success — the user would otherwise
 * bounce back to /login with no feedback.
 */

const LOGIN_PATH = '/api/v1/auth/login';
const REFRESH_PATH = '/api/v1/auth/refresh';

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const errorBody = (path: string) => ({
  timestamp: new Date().toISOString(),
  status: 401,
  error: 'Unauthorized',
  code: 'auth_bad_credentials',
  message: 'msg',
  path,
  traceId: '',
  fields: [],
});

describe('authStore.login session bootstrap', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ user: null, isAuthenticated: false, isSubmitting: false });
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns false and notifies when /me fails after a successful login', async () => {
    const errorSpy = vi.spyOn(notify, 'error').mockImplementation((msg: string) => msg);
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url === LOGIN_PATH) {
        expect(init?.method).toBe('POST');
        return json({ accessToken: 't', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 }, 200);
      }
      // /me 401s (access token rejected) and the transparent refresh cannot recover.
      return json(errorBody(url), 401);
    }));

    const ok = await useAuthStore.getState().login('admin@acme.dev', 'secret');

    expect(ok).toBe(false);
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(errorSpy).toHaveBeenCalledExactlyOnceWith('Sign-in failed');
    errorSpy.mockRestore();
  });

  it('returns true when login and /me both succeed', async () => {
    const errorSpy = vi.spyOn(notify, 'error').mockImplementation((msg: string) => msg);
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === LOGIN_PATH) {
        return json({ accessToken: 't', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 }, 200);
      }
      if (url === REFRESH_PATH) {
        return json({ accessToken: 't2', refreshToken: 'r2', tokenType: 'Bearer', expiresIn: 900 }, 200);
      }
      return json({ id: 'u1', email: 'admin@acme.dev', authorities: [] }, 200);
    }));

    const ok = await useAuthStore.getState().login('admin@acme.dev', 'secret');

    expect(ok).toBe(true);
    expect(useAuthStore.getState().isAuthenticated).toBe(true);
    expect(errorSpy).not.toHaveBeenCalled();
    errorSpy.mockRestore();
  });
});
