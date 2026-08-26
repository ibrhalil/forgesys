package com.ibrhalil.forgesys.entity;

/**
 * Custom app property type (K-15); each type has a backend validator
 * ({@code AppPropertyValueValidator}). {@link #FORMULA} is deferred — creation is
 * rejected until expression evaluation + sandboxing land.
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
