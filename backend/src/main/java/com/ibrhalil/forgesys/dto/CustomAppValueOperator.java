package com.ibrhalil.forgesys.dto;

/**
 * Filter operators over custom app property values (K-15); wire value is the
 * enum name. Type support is decided by {@code CustomAppQueryValidator}; empty cells
 * match only IS_EMPTY / IS_NOT_EMPTY.
 */
public enum CustomAppValueOperator {
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
