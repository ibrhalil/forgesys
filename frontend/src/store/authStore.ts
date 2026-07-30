import { create } from 'zustand';
import { authApi } from '../features/auth/authApi';
import type { MeResponse } from '../features/auth/types';
import { ApiError, setSessionExpiredHandler } from '../lib/api';
import { notify } from '../lib/notify';
import { t } from '../lib/i18n';

interface AuthState {
  user: MeResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isSubmitting: boolean;

  login: (email: string, password: string) => Promise<boolean>;
  fetchMe: () => Promise<void>;
  logout: () => Promise<void>;
  /** Forced logout: the refresh token is gone/revoked (apiFetch signals this on a 401 it can't recover from). Clears auth so RequireAuth redirects to /login. */
  sessionExpired: () => void;
  hasAuthority: (authority: string) => boolean;
  hasAnyAuthority: (...authorities: string[]) => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  // Bootstrap-only: true until the first /me check completes. The whole app gates on
  // this (App.tsx) — it must NOT be reused for login submission, or the router unmounts
  // mid-login and breaks the post-login navigate().
  isLoading: true,
  isSubmitting: false,

  login: async (email, password) => {
    set({ isSubmitting: true });
    try {
      await authApi.login({ email, password });
      await get().fetchMe();
      return true;
    } catch (e) {
      // Auth failures (bad credentials, locked account) surface as a toast — they are
      // not field-level validation.
      notify.error(e instanceof ApiError ? e.body.message : t('auth.loginFailed'));
      return false;
    } finally {
      set({ isSubmitting: false });
    }
  },

  fetchMe: async () => {
    try {
      const user = await authApi.me();
      set({ user, isAuthenticated: true, isLoading: false });
    } catch {
      set({ user: null, isAuthenticated: false, isLoading: false });
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      set({ user: null, isAuthenticated: false });
    }
  },

  sessionExpired: () => {
    // isLoading is intentionally left as-is (false after bootstrap) so RequireAuth
    // redirects to /login instead of hanging on the loading spinner.
    set({ user: null, isAuthenticated: false });
  },

  hasAuthority: (authority) => {
    const { user } = get();
    return user?.authorities?.includes(authority) ?? false;
  },

  hasAnyAuthority: (...authorities) => {
    const { user } = get();
    if (!user?.authorities) return false;
    return authorities.some((a) => user.authorities.includes(a));
  },
}));

// Wire apiFetch's "session unrecoverable" signal to the store. Decoupled via the
// setter so lib/api never imports this store (no circular dependency).
setSessionExpiredHandler(() => useAuthStore.getState().sessionExpired());
