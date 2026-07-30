import type { RoleSummary } from '../../types';
import type { Permission } from '../permissions/types';

// Full role (RoleResponse) — used by /roles endpoints
export interface Role {
  id: string;
  name: string;
  description: string | null;
  /** True when the role implicitly holds every permission (all_permissions flag). */
  allPermissions: boolean;
  permissions: Permission[];
  /** Roles this one inherits permissions from (Faz 4a role inheritance). */
  parents?: RoleSummary[];
}

export interface CreateRoleRequest {
  name: string;
  description?: string;
}

export interface AssignPermissionsRequest {
  /** Explicit permission ids to assign (ignored when `all` is true). */
  permissionIds?: string[];
  /** When true, sets the role's all_permissions flag (implicit all permissions). */
  all?: boolean;
}

export interface AssignRolesRequest {
  roleIds: string[];
}
