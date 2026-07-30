package com.ibrhalil.forgesys.web.filter;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Coarse value type of a filterable/sortable field. Determines the set of supported
 * {@link FilterOperator}s and how wire values are parsed. Kept intentionally small —
 * numeric filtering has no direct entity attribute today and can be added when a real
 * field needs it (a {@code NUMERIC} kind plus parser branch).
 */
public enum FilterFieldType {

    STRING(EnumSet.of(FilterOperator.EQ, FilterOperator.NOT_EQ, FilterOperator.IN, FilterOperator.NOT_IN,
            FilterOperator.CONTAINS, FilterOperator.STARTS_WITH, FilterOperator.ENDS_WITH,
            FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL), String.class),

    UUID(EnumSet.of(FilterOperator.EQ, FilterOperator.NOT_EQ, FilterOperator.IN, FilterOperator.NOT_IN,
            FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL), UUID.class),

    BOOLEAN(EnumSet.of(FilterOperator.EQ, FilterOperator.NOT_EQ,
            FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL), Boolean.class),

    TEMPORAL(EnumSet.of(FilterOperator.EQ, FilterOperator.NOT_EQ,
            FilterOperator.GT, FilterOperator.GTE, FilterOperator.LT, FilterOperator.LTE,
            FilterOperator.BETWEEN, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL), OffsetDateTime.class),

    ENUM(EnumSet.of(FilterOperator.EQ, FilterOperator.NOT_EQ, FilterOperator.IN, FilterOperator.NOT_IN,
            FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL), null);

    private final Set<FilterOperator> supportedOperators;
    private final Class<?> defaultJavaType;

    FilterFieldType(Set<FilterOperator> supportedOperators, Class<?> defaultJavaType) {
        this.supportedOperators = Set.copyOf(supportedOperators);
        this.defaultJavaType = defaultJavaType;
    }

    public boolean supports(FilterOperator operator) {
        return supportedOperators.contains(operator);
    }

    /** Default parsed value type; only meaningful for self-describing kinds (null for ENUM). */
    public Class<?> javaType() {
        return defaultJavaType;
    }
}
