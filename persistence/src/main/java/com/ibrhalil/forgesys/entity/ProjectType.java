package com.ibrhalil.forgesys.entity;

/**
 * Determines which built-in modules are available inside a {@link Project} (the
 * type-driven lightweight module system). Adding a value here + wiring its UI makes a
 * new project kind available; the type is immutable product behaviour, not plan-driven
 * activation (billing/subscription is a separate concern — Faz 6).
 */
public enum ProjectType {
    /** Task-management project (board with TODO/IN_PROGRESS/DONE). */
    TASKS,
    /** Notes project (placeholder — module arrives later). */
    NOTES
}
