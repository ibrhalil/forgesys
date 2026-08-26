package com.ibrhalil.forgesys.entity;

/**
 * Content behaviour of a {@link Project} container (K-45). The creatable-type catalog
 * derives from the tenant's ACTIVE modules; changing the type while the project holds
 * content of the current type is rejected.
 */
public enum ProjectType {
    /** Task-management project (board with TODO/IN_PROGRESS/DONE). */
    TASKS,
    /** Notes project — note categories and markdown notes anchored to the container. */
    NOTES,
    /** App-collection project — hosts the tenant's custom apps (K-15 amend). */
    APPS
}
