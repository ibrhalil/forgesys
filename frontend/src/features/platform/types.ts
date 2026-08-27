import type { CompanyStatus } from '../auth/types';
import type { SearchOrListParams } from '../../types';

// ─── Platform auth (K-50 F2) ───

export interface PlatformLoginRequest {
  email: string;
  password: string;
}

// Backend PlatformLoginResponse: tokens in body (SPA relies on the httpOnly
// sf_platform_* cookies) + userId/email/displayName/authorities.
export interface PlatformLoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  displayName: string;
  authorities: string[];
}

// Backend PlatformMeResponse (GET /api/v1/platform/me).
export interface PlatformMeResponse {
  userId: string;
  email: string;
  displayName: string;
  userType: 'HUMAN' | 'SERVICE';
  authorities: string[];
}

// ─── Companies (K-50 F3/F4) ───

export interface PlatformCompany {
  id: string;
  name: string;
  subdomain: string;
  status: CompanyStatus;
}

export interface CompanyStatusUpdateRequest {
  status: CompanyStatus;
}

export interface SubscriptionResponse {
  companyId: string;
  planKey: string;
  planName: string;
  status: string;
  startedAt: string | null;
}

export interface SubscriptionUpdateRequest {
  planKey: string;
}

export interface ModuleActivation {
  key: string;
  active: boolean;
}

export interface CompanyModulesUpdateRequest {
  activations: ModuleActivation[];
}

export interface PlatformModule {
  key: string;
  name: string;
  minPlan: string;
  active: boolean;
  allowedByPlan: boolean;
}

export interface CompanyReportResponse {
  companyId: string;
  userCount: number;
  projectCount: number;
  appCount: number;
  noteCount: number;
}

// ─── Tenant switch / impersonation (K-50 F6) ───

export interface PlatformSwitchStartRequest {
  reason: string;
}

export interface PlatformSwitchStartResponse {
  switchCode: string;
  targetUrl: string;
}

// ─── Service accounts (K-50 F5) ───

export interface ServiceAccountCreateRequest {
  name: string;
  scopes: string[];
  expiresAt?: string;
}

// Raw key shown EXACTLY once at creation — never persisted or re-fetched.
export interface ServiceAccountCreatedResponse {
  id: string;
  accountId: string;
  name: string;
  scopes: string[];
  keyPrefix: string;
  expiresAt: string | null;
  rawKey: string;
}

export interface ServiceAccount {
  id: string;
  accountId: string;
  name: string;
  scopes: string[];
  keyPrefix: string;
  expiresAt: string | null;
  lastUsedAt: string | null;
  revokedAt: string | null;
  enabled: boolean;
  createdAt: string;
}

// ─── Platform audit trail (K-50 F7) ───

export interface PlatformAuditEntry {
  id: string;
  actorId: string | null;
  actorType: string;
  action: string;
  targetType: string | null;
  targetId: string | null;
  detail: string | null;
  ipAddress: string | null;
  traceId: string | null;
  createdAt: string;
}

// ─── Mail testing (K-51) ───

export type MailChannel = 'SMTP' | 'LOG' | 'IN_MEMORY';

export interface PlatformMailTemplateInfo {
  name: string;
  key: string;
  subjectTr: string;
  subjectEn: string;
}

// Backend PlatformMailInfoResponse — what a send actually does in this profile.
export interface PlatformMailInfo {
  channel: MailChannel;
  from: string;
  defaultLanguage: string;
  templatesDir: string;
  templates: PlatformMailTemplateInfo[];
}

export interface PlatformMailSampleData {
  template: string;
  language: 'tr' | 'en';
  firstName?: string;
  organizationName?: string;
  actionUrl?: string;
  expiresInHours?: number;
}

export interface PlatformMailPreview {
  subject: string;
  bodyHtml: string;
}

export interface PlatformMailTestSendRequest extends PlatformMailSampleData {
  recipient: string;
}

export interface PlatformMailTestSendResponse {
  channel: MailChannel;
  recipient: string;
  template: string;
  language: string;
}

// ─── List params ───

export type PlatformCompanyParams = SearchOrListParams & { status?: CompanyStatus };

export type PlatformAuditParams = SearchOrListParams & {
  action?: string;
  actorId?: string;
  targetType?: string;
  fromDate?: string;
  toDate?: string;
};
