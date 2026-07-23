// Auth
export interface LoginRequest {
  email: string;
  password: string;
}

// Backend LoginResponse: accessToken, tokenType, expiresIn, userId, email, authorities
// (no tenant field — tenant comes from /me)
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  authorities: string[];
}

export interface MeResponse {
  userId: string;
  email: string;
  tenant: string;
  authorities: string[];
}

// RBAC
export interface Permission {
  id: string;
  name: string;
  description: string | null;
}

// Full role (RoleResponse) — used by /roles endpoints
export interface Role {
  id: string;
  name: string;
  description: string | null;
  permissions: Permission[];
}

// Lightweight references embedded inside User/Group responses (RoleSummary/GroupSummary
// on the backend — only id + name, never the permission graph)
export interface RoleSummary {
  id: string;
  name: string;
}

export interface GroupSummary {
  id: string;
  name: string;
}

// Full group (GroupResponse) — nested roles are summaries
export interface Group {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
  roles: RoleSummary[];
  memberCount: number;
}

// UserResponse — roles/groups are summaries
export interface User {
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
  roles: RoleSummary[];
  groups: GroupSummary[];
}

// Platform
export type CompanyStatus = 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED';

export interface Company {
  id: string;
  name: string;
  subdomain: string;
  emailDomain: string;
  schemaName: string;
  dbRole: string;
  status: CompanyStatus;
}

// API error — mirrors backend ApiErrorResponse
export interface ApiFieldError {
  field: string;
  rejectedValue: unknown;
  message: string;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  traceId: string;
  fields: ApiFieldError[];
}

// Create/Update DTOs
export interface CreateRoleRequest {
  name: string;
  description?: string;
}

export interface CreateGroupRequest {
  name: string;
  description?: string;
  active?: boolean;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  username?: string;
  firstName?: string;
  lastName?: string;
  enabled?: boolean;
}

export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  enabled?: boolean;
}

export interface UserProfileUpdateRequest {
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
  address?: string;
  city?: string;
  country?: string;
  zipCode?: string;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AdminPasswordResetRequest {
  newPassword: string;
}

export interface AssignPermissionsRequest {
  permissionIds: string[];
}

export interface AssignRolesRequest {
  roleIds: string[];
}

export interface AssignGroupsRequest {
  groupIds: string[];
}

export interface AssignMembersRequest {
  userIds: string[];
}

export interface CompanyStatusUpdateRequest {
  status: CompanyStatus;
}

// Pagination
export interface PageParams {
  page?: number;
  size?: number;
  /** Spring Data sort, e.g. "email" or "email,desc". */
  sort?: string;
}

/**
 * Raw Spring Data {@code Page<T>} response. Tolerant of both layouts: the legacy flat
 * one (totalElements/number/size at the root) and the Spring Boot >=3.3 nested
 * {@code page} object. See {@link normalizePage}.
 */
export interface PageResponse<T> {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
  page?: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

/** Normalized pagination result consumed by the UI. */
export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
