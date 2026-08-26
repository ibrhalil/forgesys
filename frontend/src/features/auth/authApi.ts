import { api } from '../../lib/api';
import type {
  LoginRequest, LoginResponse, MeResponse, EmailVerificationResponse, MessageResponse,
} from './types';

export const authApi = {
  login: (data: LoginRequest) =>
    api.post<LoginResponse>('/api/v1/auth/login', data),

  me: () => api.get<MeResponse>('/api/v1/users/me'),

  logout: () => api.post<void>('/api/v1/auth/logout'),

  /** Consumes a single-use email-verification token (optional-policy flow). */
  verifyEmail: (token: string) =>
    api.post<EmailVerificationResponse>('/api/v1/auth/verify-email', { token }),

  /** Always returns 200 — unknown addresses are indistinguishable from success. */
  forgotPassword: (email: string) =>
    api.post<MessageResponse>('/api/v1/auth/forgot-password', { email }),

  resetPassword: (token: string, newPassword: string) =>
    api.post<MessageResponse>('/api/v1/auth/reset-password', { token, newPassword }),

  /** K-50 F6: exchanges a one-time platform switch code for an impersonation session. */
  exchangePlatformSwitch: (code: string) =>
    api.post<LoginResponse>('/api/v1/auth/platform-switch', { code }),
};
