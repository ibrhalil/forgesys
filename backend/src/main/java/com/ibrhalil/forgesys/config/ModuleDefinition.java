package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.ProjectType;

import java.util.List;
import java.util.Optional;

/**
 * Code-side registry of platform modules (K-16) — a module is code (entities, services,
 * migrations), so its metadata ships with the code; {@code t_tenant_modules} stores only
 * per-tenant activation state keyed by {@link #key}. {@code minPlan} gates activation
 * (plan rank &gt;= module rank); {@code ownMigrations} = module ships tenant migrations
 * under {@code db/migration/module/{key}} with a module-scoped Flyway history. WARNING:
 * module locations MUST live OUTSIDE {@code db/migration/tenant} — recursive Flyway
 * scanning would swallow module versions into the core history (duplicate versions).
 */
public enum ModuleDefinition {

    PM("pm", "Projects & Tasks", PlanDefinition.FREE, false, ProjectType.TASKS, List.of(
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_PROJECT_READ, "Read tenant projects"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_PROJECT_WRITE, "Create or update tenant projects"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_PROJECT_DELETE, "Delete tenant projects"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_TASK_READ, "Read project tasks"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_TASK_WRITE, "Create or update project tasks"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_TASK_DELETE, "Delete project tasks")
    )),

    /**
     * First {@code ownMigrations=true} module: tables under {@code db/migration/module/apps},
     * history {@code flyway_schema_history_mod_apps}. {@code minPlan=FREE} — tiers separate
     * by plan limits (maxApps/maxRecordsPerApp, soft-block).
     */
    APPS("apps", "Custom App Builder", PlanDefinition.FREE, true, ProjectType.APPS, List.of(
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_APP_READ, "Read tenant custom apps (definitions, properties, views)"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_APP_WRITE, "Create or update tenant custom apps and their properties/views"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_APP_DELETE, "Delete tenant custom apps"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_RECORD_READ, "Read records of tenant custom apps"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_RECORD_WRITE, "Create or update custom app records"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_RECORD_DELETE, "Delete custom app records")
    )),

    /** K-44: {@code ownMigrations=true} ({@code db/migration/module/notes}); visibility tenant-shared, personal/ABAC deferred. */
    NOTES("notes", "Notes", PlanDefinition.FREE, true, ProjectType.NOTES, List.of(
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.NOTES_NOTE_READ, "Read tenant notes"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.NOTES_NOTE_WRITE, "Create or update tenant notes"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.NOTES_NOTE_DELETE, "Delete tenant notes"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.NOTES_CATEGORY_READ, "Read note categories"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.NOTES_CATEGORY_WRITE, "Create or update note categories")
    ));

    /** Convention for per-module tenant migration locations ({@code %s} = module key). */
    public static final String FLYWAY_LOCATION_PATTERN = "classpath:db/migration/module/%s";

    private final String key;
    private final String displayName;
    private final PlanDefinition minPlan;
    private final boolean ownMigrations;
    private final ProjectType projectType;
    private final List<PermissionCatalog.PermissionDefinition> permissions;

    ModuleDefinition(String key, String displayName, PlanDefinition minPlan, boolean ownMigrations,
            ProjectType projectType, List<PermissionCatalog.PermissionDefinition> permissions) {
        this.key = key;
        this.displayName = displayName;
        this.minPlan = minPlan;
        this.ownMigrations = ownMigrations;
        this.projectType = projectType;
        this.permissions = List.copyOf(permissions);
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public PlanDefinition minPlan() {
        return minPlan;
    }

    /** The project type whose content this module supplies, or {@code null} (K-45). */
    public ProjectType projectType() {
        return projectType;
    }

    public List<PermissionCatalog.PermissionDefinition> permissions() {
        return permissions;
    }

    /** Module migration location, or null when its tables ship in the core tenant baseline. */
    public String flywayLocation() {
        return ownMigrations ? FLYWAY_LOCATION_PATTERN.formatted(key) : null;
    }

    public static Optional<ModuleDefinition> fromKey(String key) {
        for (ModuleDefinition module : values()) {
            if (module.key.equals(key)) {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }

    /** The module supplying the given project type's content, if any (K-45). */
    public static Optional<ModuleDefinition> forProjectType(ProjectType projectType) {
        for (ModuleDefinition module : values()) {
            if (module.projectType == projectType) {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }
}
