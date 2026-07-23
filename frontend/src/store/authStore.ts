import { create } from 'zustand';
import type { MeResponse } from '../types';
import { authApi } from '../api/auth';
import { ApiError } from '../lib/api';

interface AuthState {
  user: MeResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isSubmitting: boolean;
  error: string | null;

  login: (email: string, password: string) => Promise<boolean>;
  fetchMe: () => Promise<void>;
  logout: () => Promise<void>;
  hasAuthority: (authority: string) => boolean;
  hasAnyAuthority: (...authorities: string[]) => boolean;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  // Bootstrap-only: true until the first /me check completes. The whole app gates on
  // this (App.tsx) — it must NOT be reused for login submission, or the router unmounts
  // mid-login and breaks the post-login navigate().
  isLoading: true,
  isSubmitting: false,
  error: null,

  login: async (email, password) => {
    set({ isSubmitting: true, error: null });
    try {
      await authApi.login({ email, password });
      await get().fetchMe();
      return true;
    } catch (e) {
      const message = e instanceof ApiError ? e.body.message : 'Login failed';
      set({ error: message });
      return false;
    } finally {
      set({ isSubmitting: false });
    }
  },

  fetchMe: async () => {
    try {
      const user = await authApi.me();
      set({ user, isAuthenticated: true, isLoading: false, error: null });
    } catch {
      set({ user: null, isAuthenticated: false, isLoading: false });
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      set({ user: null, isAuthenticated: false, error: null });
    }
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

  clearError: () => set({ error: null }),
}));
