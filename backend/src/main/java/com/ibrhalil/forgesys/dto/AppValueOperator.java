package com.ibrhalil.forgesys.dto;

/**
 * Filter operators over custom app property values (K-15 / Epic 3.0.B). Wire value is
 * the enum name (e.g. {@code "EQ"}) — mirrors the generic filter engine's
 * {@code FilterOperator}. Which operators a {@code PropertyType} accepts is decided by
 * {@code AppQueryValidator}; value semantics are documented per operator in
 * {@code AppRecordSearchExecutor} (empty cells match only IS_EMPTY / IS_NOT_EMPTY).
 */
public enum AppValueOperator {
    EQ,
    NOT_EQ,
    CONTAINS,
    GT,
    GTE,
    LT,
    LTE,
    IS_EMPTY,
    IS_NOT_EMPTY
}
