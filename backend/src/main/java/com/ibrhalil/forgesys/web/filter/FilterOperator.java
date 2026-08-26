package com.ibrhalil.forgesys.web.filter;

/**
 * Filter operators supported by the list filter engine; wire value is the enum name.
 * {@code TRUE}/{@code FALSE} are deliberately absent — {@code EQ} on a boolean covers
 * them with one code path.
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
