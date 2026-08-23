package com.ibrhalil.forgesys.entity;

/**
 * The content behaviour of a {@link Project} container (K-45 typed project container).
 * The available-type catalog derives from the tenant's ACTIVE modules
 * ({@code ModuleDefinition.projectType}): a module's activation makes its type
 * creatable, deactivation leaves existing projects of that type read-only. The type
 * says nothing about management/configuration ownership (deliberately out of scope).
 * Changing a type while the project holds content of the current type is rejected.
 */
public enum ProjectType {
    /** Task-management project (board with TODO/IN_PROGRESS/DONE). */
    TASKS,
    /** Notes project — note categories and markdown notes anchored to the container. */
    NOTES,
    /** App-collection project — hosts the tenant's custom apps (K-15 amend). */
    APPS
}
