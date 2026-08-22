package com.ibrhalil.forgesys.entity;

/**
 * Custom app view type (K-15 / Epic 3.0.B) — how a view renders its records.
 * Rendering arrives with the frontend (Epic 4.2); the backend stores the type plus a
 * structured {@code config} JSON (filters/sorts/grouping validated by
 * {@code AppViewConfigValidator}).
 */
public enum ViewType {
    TABLE,
    BOARD,
    CALENDAR,
    GALLERY,
    LIST
}
