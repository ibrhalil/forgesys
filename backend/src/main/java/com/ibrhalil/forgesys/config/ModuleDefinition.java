package com.ibrhalil.forgesys.config;

import java.util.List;
import java.util.Optional;

/**
 * Code-side registry of platform modules (K-16 / Epic 3.0.A). The single source of truth
 * for module metadata — replaces a {@code t_module_catalog} DB table on purpose: a module
 * is code (entities, services, migrations), so its registry entry must ship with the code
 * and cannot drift from it. {@code t_tenant_modules} stores only the per-tenant activation
 * state keyed by {@link #key}.
 *
 * <ul>
 *   <li>{@code minPlan} — the cheapest {@link PlanDefinition} that may activate the module;
 *       activation checks tenant plan rank &gt;= module min rank.</li>
 *   <li>{@code ownMigrations} — whether the module ships its own tenant migrations under
 *       {@code db/migration/module/{key}} (run with a module-scoped Flyway history
 *       table, isolated from the core tenant history). {@code false} = tables already ship
 *       in the core tenant baseline (true for {@code pm}, whose tables predate the module
 *       system). NOTE: module locations MUST live OUTSIDE {@code db/migration/tenant} —
 *       Flyway location scanning is recursive and would swallow module versions into the
 *       core tenant history (duplicate-version collisions).</li>
 *   <li>{@code permissions} — the module's permission definitions, seeded into the tenant's
 *       {@code t_permissions} on activation (and re-synced at startup for activated modules).</li>
 * </ul>
 */
public enum ModuleDefinition {

    PM("pm", "Projects & Tasks", PlanDefinition.FREE, false, List.of(
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_PROJECT_READ, "Read tenant projects"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_PROJECT_WRITE, "Create or update tenant projects"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_PROJECT_DELETE, "Delete tenant projects"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_TASK_READ, "Read project tasks"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_TASK_WRITE, "Create or update project tasks"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.PM_TASK_DELETE, "Delete project tasks")
    )),

    /**
     * Custom App Builder (K-15 / Epic 3.0.B). The first module with {@code ownMigrations
     * = true}: its tables ship under {@code db/migration/module/apps} and land in the
     * tenant schema on activation (per-module Flyway history {@code
     * flyway_schema_history_mod_apps}). {@code minPlan = FREE} — adoption is the point;
     * plans separate by the {@link PlanDefinition} limits (maxApps / maxRecordsPerApp),
     * enforced as a soft-block.
     */
    APPS("apps", "Custom App Builder", PlanDefinition.FREE, true, List.of(
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_APP_READ, "Read tenant custom apps (definitions, properties, views)"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_APP_WRITE, "Create or update tenant custom apps and their properties/views"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_APP_DELETE, "Delete tenant custom apps"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_RECORD_READ, "Read records of tenant custom apps"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_RECORD_WRITE, "Create or update custom app records"),
            new PermissionCatalog.PermissionDefinition(PermissionCatalog.APPS_RECORD_DELETE, "Delete custom app records")
    )),

    /**
     * Notes (K-44 / Epic 3.2) — standalone tenant-shared notes with categories and
     * markdown content. {@code ownMigrations = true} (the APPS pattern): tables ship
     * under {@code db/migration/module/notes} and land in the tenant schema on
     * activation (per-module history {@code flyway_schema_history_mod_notes}).
     * {@code minPlan = FREE}, no plan limits (pm convention). Visibility is
     * tenant-shared ({@code notes:note:read} sees all tenant notes) — personal/ABAC
     * notes were consciously deferred.
     */
    NOTES("notes", "Notes", PlanDefinition.FREE, true, List.of(
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
    private final List<PermissionCatalog.PermissionDefinition> permissions;

    ModuleDefinition(String key, String displayName, PlanDefinition minPlan, boolean ownMigrations,
            List<PermissionCatalog.PermissionDefinition> permissions) {
        this.key = key;
        this.displayName = displayName;
        this.minPlan = minPlan;
        this.ownMigrations = ownMigrations;
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

    public List<PermissionCatalog.PermissionDefinition> permissions() {
        return permissions;
    }

    /**
     * Flyway location for the module's own tenant migrations, or {@code null} when the
     * module's tables ship in the core tenant baseline (nothing extra to run).
     */
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
}
