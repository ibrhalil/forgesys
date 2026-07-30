package com.ibrhalil.forgesys.web.filter;

/**
 * Filter operators supported by the list filter engine. Wire value is the enum name
 * (e.g. {@code "EQ"}). Which operators a field accepts is decided by its
 * {@link FilterFieldType}; {@code TRUE}/{@code FALSE} pseudo-operators are deliberately
 * absent — {@code EQ} on a boolean covers them with one code path.
 */
public enum FilterOperator {
    EQ,
    NOT_EQ,
    IN,
    NOT_IN,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    GT,
    GTE,
    LT,
    LTE,
    BETWEEN,
    IS_NULL,
    IS_NOT_NULL
}
