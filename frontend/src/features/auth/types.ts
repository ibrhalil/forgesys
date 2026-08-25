import type { GroupSummary, RoleSummary } from '../../types';

// Auth
export interface LoginRequest {
  email: string;
  password: string;
}

// Backend LoginResponse: accessToken, refreshToken, tokenType, expiresIn, userId,
// email, authorities (no tenant field — tenant comes from /me). Both tokens are also
// set as httpOnly cookies (sf_access_token, sf_refresh_token); the body copies exist
// for non-browser clients. The SPA relies on the cookies, not these fields.
export interface LoginResponse {
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  authorities: string[];
}

// Backend MeResponse: the single self endpoint (GET /users/me) — full profile view
// from the DB + the authorities embedded in the access token. The former
// claims-only GET /auth/me was removed (K-37).
export interface MeResponse {
  id: string;
  email: string;
  username: string;
  firstName: string | null;
  lastName: string | null;
  phoneNumber: string | null;
  address: string | null;
  city: string | null;
  country: string | null;
  zipCode: string | null;
  enabled: boolean;
  emailVerified: boolean;
  lockedUntil: string | null;
  roles: RoleSummary[];
  groups: GroupSummary[];
  authorities: string[];
}

// K-21 tenant signup — two-phase provisioning (register -> email verify -> activate)
export interface RegisterRequest {
  companyName: string;
  subdomain: string;
  adminEmail: string;
  adminPassword: string;
  adminFirstName?: string;
  adminLastName?: string;
}

export type CompanyStatus = 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED';

// 202 Accepted response of POST /api/v1/auth/company/register (Company is PROVISIONING)
export interface RegisterResponse {
  companyId: string;
  name: string;
  subdomain: string;
  status: CompanyStatus;
  message: string | null;
}

export interface VerifyTenantRequest {
  token: string;
}

// 200 OK response of POST /api/v1/auth/company/verify (Company promoted to ACTIVE)
export interface VerifyTenantResponse {
  companyId: string;
  name: string;
  subdomain: string;
  status: CompanyStatus;
  message: string | null;
}

export interface SuggestSubdomainRequest {
  name: string;
}

export interface SuggestSubdomainResponse {
  suggestions: string[];
}

// 200 OK response of POST /api/v1/auth/verify-email (user lifecycle, optional policy)
export interface EmailVerificationResponse {
  message: string;
}

// Uniform {message} responses of the public auth actions (verify-email,
// forgot-password, reset-password)
export type MessageResponse = EmailVerificationResponse;
