import { create } from 'zustand';
import { platformAuthApi } from '../features/platform/authApi';
import type { PlatformMeResponse } from '../features/platform/types';
import { ApiError, setPlatformSessionExpiredHandler } from '../lib/platformApi';
import { notify } from '../lib/notify';
import { t } from '../lib/i18n';

interface PlatformAuthState {
  user: PlatformMeResponse | null;
  isAuthenticated: boolean;
  /** Bootstrap-only: true until the first platform /me check completes. */
  isLoading: boolean;
  isSubmitting: boolean;

  login: (email: string, password: string) => Promise<boolean>;
  fetchMe: () => Promise<void>;
  logout: () => Promise<void>;
  /** Forced logout: the platform refresh token is gone/revoked — clears the session. */
  sessionExpired: () => void;
  hasAuthority: (authority: string) => boolean;
}

/**
 * Platform console session (K-50), parallel to {@link useAuthStore}: separate
 * cookies ({@code sf_platform_*}), separate /me, separate expiry signal. The
 * tenant session is untouched by platform login/logout and vice versa.
 */
export const usePlatformAuthStore = create<PlatformAuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true,
  isSubmitting: false,

  login: async (email, password) => {
    set({ isSubmitting: true });
    try {
      await platformAuthApi.login({ email, password });
      await get().fetchMe();
      return true;
    } catch (e) {
      notify.error(e instanceof ApiError ? e.body.message : t('platform.loginFailed'));
      return false;
    } finally {
      set({ isSubmitting: false });
    }
  },

  fetchMe: async () => {
    try {
      const user = await platformAuthApi.me();
      set({ user, isAuthenticated: true, isLoading: false });
    } catch {
      set({ user: null, isAuthenticated: false, isLoading: false });
    }
  },

  logout: async () => {
    try {
      await platformAuthApi.logout();
    } finally {
      set({ user: null, isAuthenticated: false });
    }
  },

  sessionExpired: () => {
    set({ user: null, isAuthenticated: false });
  },

  hasAuthority: (authority) => {
    const { user } = get();
    return user?.authorities?.includes(authority) ?? false;
  },
}));

// Wire the platform client's "session unrecoverable" signal to the store.
setPlatformSessionExpiredHandler(() => usePlatformAuthStore.getState().sessionExpired());
