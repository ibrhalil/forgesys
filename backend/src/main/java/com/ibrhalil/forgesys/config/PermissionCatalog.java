package com.ibrhalil.forgesys.config;

import java.util.List;

/**
 * Catalog of built-in permissions seeded into every tenant schema by
 * {@link RbacSeeder}. Permission names follow {@code {module}:{resource}:{action}}.
 *
 * <p>Two namespaces:
 * <ul>
 *   <li>{@code iam:*} — tenant-scoped identity/admin operations (User/Role/Permission/Group
 *       CRUD), enforced by {@code @PreAuthorize} on the RBAC controllers.</li>
 *   <li>{@code platform:*} — cross-tenant/platform operations (company management), reserved
 *       for the system tenant admin; enforced on {@code /api/v1/platform/**}.</li>
 * </ul>
 *
 * <p>The built-in {@code Admin} role is granted every permission in {@link #ALL}, so growing
 * this catalog automatically keeps Admin complete (the seeder re-syncs the role's permission
 * set on every startup).
 */
public final class PermissionCatalog {

    public static final String ADMIN_ROLE_NAME = "Admin";

    public static final String IAM_USER_READ = "iam:user:read";
    public static final String IAM_USER_WRITE = "iam:user:write";
    public static final String IAM_USER_DELETE = "iam:user:delete";
    public static final String IAM_ROLE_READ = "iam:role:read";
    public static final String IAM_ROLE_WRITE = "iam:role:write";
    public static final String IAM_ROLE_DELETE = "iam:role:delete";
    public static final String IAM_PERMISSION_READ = "iam:permission:read";
    public static final String IAM_PERMISSION_WRITE = "iam:permission:write";
    public static final String IAM_GROUP_READ = "iam:group:read";
    public static final String IAM_GROUP_WRITE = "iam:group:write";
    public static final String IAM_GROUP_DELETE = "iam:group:delete";
    public static final String IAM_AUDIT_READ = "iam:audit:read";

    public static final String PLATFORM_COMPANY_READ = "platform:company:read";
    public static final String PLATFORM_COMPANY_WRITE = "platform:company:write";

    // pm:* — project-management (the first product feature module, Faz 3 Stage 1).
    public static final String PM_PROJECT_READ = "pm:project:read";
    public static final String PM_PROJECT_WRITE = "pm:project:write";
    public static final String PM_PROJECT_DELETE = "pm:project:delete";

    public record PermissionDefinition(String name, String description) {
    }

    public static final List<PermissionDefinition> ALL = List.of(
            new PermissionDefinition(IAM_USER_READ, "Read tenant users"),
            new PermissionDefinition(IAM_USER_WRITE, "Create or update tenant users"),
            new PermissionDefinition(IAM_USER_DELETE, "Delete tenant users"),
            new PermissionDefinition(IAM_ROLE_READ, "Read tenant roles"),
            new PermissionDefinition(IAM_ROLE_WRITE, "Create or update tenant roles"),
            new PermissionDefinition(IAM_ROLE_DELETE, "Delete tenant roles"),
            new PermissionDefinition(IAM_PERMISSION_READ, "Read tenant permissions"),
            new PermissionDefinition(IAM_PERMISSION_WRITE, "Create or update tenant permissions"),
            new PermissionDefinition(IAM_GROUP_READ, "Read tenant groups"),
            new PermissionDefinition(IAM_GROUP_WRITE, "Create or update tenant groups"),
            new PermissionDefinition(IAM_GROUP_DELETE, "Delete tenant groups"),
            new PermissionDefinition(IAM_AUDIT_READ, "Read tenant audit logs and login history"),
            new PermissionDefinition(PLATFORM_COMPANY_READ, "Read platform companies"),
            new PermissionDefinition(PLATFORM_COMPANY_WRITE, "Update platform company status"),
            new PermissionDefinition(PM_PROJECT_READ, "Read tenant projects"),
            new PermissionDefinition(PM_PROJECT_WRITE, "Create or update tenant projects"),
            new PermissionDefinition(PM_PROJECT_DELETE, "Delete tenant projects")
    );

    private PermissionCatalog() {
    }
}
