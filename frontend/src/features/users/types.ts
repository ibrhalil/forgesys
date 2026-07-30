import type { RoleSummary, GroupSummary } from '../../types';

// UserResponse — roles/groups are summaries (detail endpoints)
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
  /** [RISK-22] Brute-force lockout expiry (raw); null = not locked. Expiry is lazy — compare against now. */
  lockedUntil: string | null;
  roles: RoleSummary[];
  groups: GroupSummary[];
}

// UserDirectoryViewResponse — flat list-item projection (GET /users, POST /users/search):
// the join and counts run in the DB (directory view entity); no association lists.
export interface UserDirectoryView {
  id: string;
  username: string;
  email: string;
  emailVerified: boolean;
  firstName: string | null;
  lastName: string | null;
  enabled: boolean;
  lockedUntil: string | null;
  lastLoginAt: string | null;
  createdDate: string;
  roleCount: number;
  groupCount: number;
}

/**
 * "Currently locked" — the backend clears `lockedUntil` lazily (on the next login
 * attempt or admin unlock), so a stale past timestamp must NOT render as locked.
 */
export function isLocked(user: Pick<UserDirectoryView, 'lockedUntil'>): boolean {
  return !!user.lockedUntil && new Date(user.lockedUntil).getTime() > Date.now();
}

// UserActivityResponse — temporal summary from GET /users/{id}/activity.
export interface UserActivity {
  createdDate: string | null;
  createdBy: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
  lastLoginAt: string | null;
  lastFailedLoginAt: string | null;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  username?: string;
  firstName?: string;
  lastName?: string;
  enabled?: boolean;
  /** Optional role ids assigned at creation. */
  roleIds?: string[];
  /** Optional group ids assigned at creation. */
  groupIds?: string[];
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

export interface AssignGroupsRequest {
  groupIds: string[];
}
