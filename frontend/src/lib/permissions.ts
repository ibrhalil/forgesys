/**
 * Canonical permission strings, mirroring the backend {@code PermissionCatalog}
 * ({@code backend/.../config/PermissionCatalog.java}). Format: `module:resource:action`.
 * The backend is the source of truth and admins can add runtime permissions — these
 * constants cover only the built-in catalog consumed by navigation and route guards.
 */
export const PERMISSIONS = {
  USER_READ: 'iam:user:read',
  USER_WRITE: 'iam:user:write',
  USER_DELETE: 'iam:user:delete',
  GROUP_MEMBER_READ: 'iam:group-member:read',
  ROLE_READ: 'iam:role:read',
  ROLE_WRITE: 'iam:role:write',
  ROLE_DELETE: 'iam:role:delete',
  GROUP_READ: 'iam:group:read',
  GROUP_WRITE: 'iam:group:write',
  GROUP_DELETE: 'iam:group:delete',
  PERMISSION_READ: 'iam:permission:read',
  PERMISSION_WRITE: 'iam:permission:write',
  PERMISSION_DELETE: 'iam:permission:delete',
  AUDIT_READ: 'iam:audit:read',
  MODULE_READ: 'iam:module:read',
  MODULE_WRITE: 'iam:module:write',
  COMPANY_READ: 'platform:company:read',
  COMPANY_WRITE: 'platform:company:write',
  PROJECT_READ: 'pm:project:read',
  PROJECT_WRITE: 'pm:project:write',
  PROJECT_DELETE: 'pm:project:delete',
  TASK_READ: 'pm:task:read',
  TASK_WRITE: 'pm:task:write',
  TASK_DELETE: 'pm:task:delete',
} as const;

export type Permission = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];
