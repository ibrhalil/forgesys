package com.ibrhalil.forgesys.config;

import java.util.List;

/**
 * Catalog of built-in <em>core</em> permissions seeded into every tenant schema by
 * {@link RbacSeeder}. Permission names follow {@code {module}:{resource}:{action}`.
 *
 * <p>Two core namespaces:
 * <ul>
 *   <li>{@code iam:*} — tenant-scoped identity/admin operations (User/Role/Permission/Group
 *       CRUD + module management), enforced by {@code @PreAuthorize} on the RBAC controllers.</li>
 *   <li>{@code platform:*} — cross-tenant/platform operations (company management), reserved
 *       for the system tenant admin; enforced on {@code /api/v1/platform/**}.</li>
 * </ul>
 *
 * <p>Product-module permissions (e.g. {@code pm:*}) are owned by their
 * {@link ModuleDefinition} and seeded on module activation (K-16 / Epic 3.0.A), not into
 * every tenant — {@link #CORE} drives only the always-present rows in {@code t_permissions}.
 *
 * <p>The built-in {@code Admin} role implicitly holds every permission via the
 * {@code all_permissions} flag (set by {@code RbacSeeder}, resolved dynamically by
 * {@code CustomUserDetailsService} — no per-permission grant rows), so it stays complete
 * without the seeder re-syncing explicit grants; module permissions reach it automatically
 * once their rows are seeded on activation.
 */
public final class PermissionCatalog {

    public static final String ADMIN_ROLE_NAME = "Admin";

    public static final String IAM_USER_READ = "iam:user:read";
    public static final String IAM_USER_WRITE = "iam:user:write";
    public static final String IAM_USER_DELETE = "iam:user:delete";
    /** Scoped read: see the members of one's own groups (+ self) instead of the whole tenant. */
    public static final String IAM_GROUP_MEMBER_READ = "iam:group-member:read";
    public static final String IAM_ROLE_READ = "iam:role:read";
    public static final String IAM_ROLE_WRITE = "iam:role:write";
    public static final String IAM_ROLE_DELETE = "iam:role:delete";
    public static final String IAM_PERMISSION_READ = "iam:permission:read";
    public static final String IAM_PERMISSION_WRITE = "iam:permission:write";
    public static final String IAM_PERMISSION_DELETE = "iam:permission:delete";
    public static final String IAM_GROUP_READ = "iam:group:read";
    public static final String IAM_GROUP_WRITE = "iam:group:write";
    public static final String IAM_GROUP_DELETE = "iam:group:delete";
    public static final String IAM_AUDIT_READ = "iam:audit:read";
    public static final String IAM_MODULE_READ = "iam:module:read";
    public static final String IAM_MODULE_WRITE = "iam:module:write";

    public static final String PLATFORM_COMPANY_READ = "platform:company:read";
    public static final String PLATFORM_COMPANY_WRITE = "platform:company:write";

    // pm:* — project-management (the first product feature module, Faz 3 Stage 1).
    // Definitions live in ModuleDefinition.PM (module-owned, seeded on activation);
    // the constants stay here as the single naming source referenced by controllers.
    public static final String PM_PROJECT_READ = "pm:project:read";
    public static final String PM_PROJECT_WRITE = "pm:project:write";
    public static final String PM_PROJECT_DELETE = "pm:project:delete";
    public static final String PM_TASK_READ = "pm:task:read";
    public static final String PM_TASK_WRITE = "pm:task:write";
    public static final String PM_TASK_DELETE = "pm:task:delete";

    // apps:* — custom app builder (K-15 / Epic 3.0.B). Definitions live in
    // ModuleDefinition.APPS (module-owned, seeded on activation); the constants stay
    // here as the single naming source referenced by controllers. Property/view CRUD
    // is covered by apps:app:write (they are part of the app definition, not data).
    public static final String APPS_APP_READ = "apps:app:read";
    public static final String APPS_APP_WRITE = "apps:app:write";
    public static final String APPS_APP_DELETE = "apps:app:delete";
    public static final String APPS_RECORD_READ = "apps:record:read";
    public static final String APPS_RECORD_WRITE = "apps:record:write";
    public static final String APPS_RECORD_DELETE = "apps:record:delete";

    // notes:* — standalone notes module (K-44 / Epic 3.2). Definitions live in
    // ModuleDefinition.NOTES (module-owned, seeded on activation); the constants stay
    // here as the single naming source referenced by controllers. Category delete is
    // covered by notes:category:write (categories are shared taxonomy, not data).
    public static final String NOTES_NOTE_READ = "notes:note:read";
    public static final String NOTES_NOTE_WRITE = "notes:note:write";
    public static final String NOTES_NOTE_DELETE = "notes:note:delete";
    public static final String NOTES_CATEGORY_READ = "notes:category:read";
    public static final String NOTES_CATEGORY_WRITE = "notes:category:write";

    public record PermissionDefinition(String name, String description) {
    }

    public static final List<PermissionDefinition> CORE = List.of(
            new PermissionDefinition(IAM_USER_READ, "Read tenant users"),
            new PermissionDefinition(IAM_USER_WRITE, "Create or update tenant users"),
            new PermissionDefinition(IAM_USER_DELETE, "Delete tenant users"),
            new PermissionDefinition(IAM_GROUP_MEMBER_READ, "Read members of own groups (scoped user visibility)"),
            new PermissionDefinition(IAM_ROLE_READ, "Read tenant roles"),
            new PermissionDefinition(IAM_ROLE_WRITE, "Create or update tenant roles"),
            new PermissionDefinition(IAM_ROLE_DELETE, "Delete tenant roles"),
            new PermissionDefinition(IAM_PERMISSION_READ, "Read tenant permissions"),
            new PermissionDefinition(IAM_PERMISSION_WRITE, "Create or update tenant permissions"),
            new PermissionDefinition(IAM_PERMISSION_DELETE, "Delete tenant permissions"),
            new PermissionDefinition(IAM_GROUP_READ, "Read tenant groups"),
            new PermissionDefinition(IAM_GROUP_WRITE, "Create or update tenant groups"),
            new PermissionDefinition(IAM_GROUP_DELETE, "Delete tenant groups"),
            new PermissionDefinition(IAM_AUDIT_READ, "Read tenant audit logs and login history"),
            new PermissionDefinition(IAM_MODULE_READ, "Read tenant modules (catalog + activation state)"),
            new PermissionDefinition(IAM_MODULE_WRITE, "Activate tenant modules"),
            new PermissionDefinition(PLATFORM_COMPANY_READ, "Read platform companies"),
            new PermissionDefinition(PLATFORM_COMPANY_WRITE, "Update platform company status")
    );

    private PermissionCatalog() {
    }
}
