import { api } from '../../lib/api';
import type { LoginRequest, LoginResponse, MeResponse } from './types';

export const authApi = {
  login: (data: LoginRequest) =>
    api.post<LoginResponse>('/api/v1/auth/login', data),

  me: () => api.get<MeResponse>('/api/v1/auth/me'),

  logout: () => api.post<void>('/api/v1/auth/logout'),
};
