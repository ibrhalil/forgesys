package com.ibrhalil.forgesys.entity;

/**
 * Custom app property type (K-15 / Epic 3.0.B). Value storage is JSONB in
 * {@code t_app_record_values.value}; each type has a matching validator in the backend
 * ({@code AppPropertyValueValidator}).
 *
 * <p>{@link #FORMULA} is <em>deferred</em>: it exists in the enum so the roadmap type
 * set is visible, but creating a FORMULA property is rejected with
 * {@code app_property_type_invalid} until expression evaluation + sandboxing land
 * (injection spike — see ROADMAP Epic 3.0.B).
 */
public enum PropertyType {
    /** Single-line text; value = JSON string. */
    TEXT,
    /** Numeric; value = JSON number (finite). */
    NUMBER,
    /** Single choice; value = JSON string, must be one of {@code config.options}. */
    SELECT,
    /** ISO-8601 date (YYYY-MM-DD); value = JSON string. */
    DATE,
    /** Tenant user reference; value = JSON string UUID (service-level existence check). */
    USER,
    /** Reference to a record of the app named in {@code config.targetAppId}; value = JSON string UUID. */
    RELATION,
    /** Deferred — creation rejected (see javadoc). */
    FORMULA
}
