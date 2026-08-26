import { platformApi } from '../../lib/platformApi';
import type { PlatformLoginResponse, PlatformMeResponse } from './types';

/** Platform auth surface (K-50 F2) — cookie-based, tenant-less. */
export const platformAuthApi = {
  login: (data: { email: string; password: string }) =>
    platformApi.post<PlatformLoginResponse>('/api/v1/platform/auth/login', data),

  me: () => platformApi.get<PlatformMeResponse>('/api/v1/platform/me'),

  logout: () => platformApi.post<void>('/api/v1/platform/auth/logout'),
};
