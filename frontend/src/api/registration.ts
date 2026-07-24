import { api } from '../lib/api';
import type {
  RegisterRequest,
  RegisterResponse,
  SuggestSubdomainRequest,
  SuggestSubdomainResponse,
  VerifyTenantRequest,
  VerifyTenantResponse,
} from '../types';

// Platform-level (no tenant context) endpoints for K-21 two-phase tenant signup.
export const registrationApi = {
  register: (data: RegisterRequest) =>
    api.post<RegisterResponse>('/api/v1/auth/company/register', data),

  verify: (data: VerifyTenantRequest) =>
    api.post<VerifyTenantResponse>('/api/v1/auth/company/verify', data),

  suggestSubdomain: (data: SuggestSubdomainRequest) =>
    api.post<SuggestSubdomainResponse>('/api/v1/auth/company/suggest-subdomain', data),
};
