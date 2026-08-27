package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.CustomAppValueFilterCriteria;
import com.ibrhalil.forgesys.dto.CustomAppValueOperator;
import com.ibrhalil.forgesys.dto.CustomAppValueSortCriteria;
import com.ibrhalil.forgesys.entity.CustomAppProperty;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared validation for property-value filter/sort clauses (K-15) — used by both
 * record search and saved view configs so the two stay in lockstep. Clauses resolve
 * eagerly against the app's property set — invalid = 400, never a mid-query 500;
 * downstream SQL references only validated UUIDs + enum fragments (injection-free).
 * Rationale: docs/CODE_NOTES.md (backend/service → CustomAppQueryValidator).
 */
@Component
public class CustomAppQueryValidator {

    /** Reserved sort key addressing record creation time instead of a property. */
    public static final String CREATED_AT_SORT = "createdAt";

    private static final Set<CustomAppValueOperator> TEXT_OPS =
            EnumSet.of(CustomAppValueOperator.EQ, CustomAppValueOperator.NOT_EQ, CustomAppValueOperator.CONTAINS,
                    CustomAppValueOperator.IS_EMPTY, CustomAppValueOperator.IS_NOT_EMPTY);
    private static final Set<CustomAppValueOperator> COMPARABLE_OPS =
            EnumSet.of(CustomAppValueOperator.EQ, CustomAppValueOperator.NOT_EQ, CustomAppValueOperator.GT,
                    CustomAppValueOperator.GTE, CustomAppValueOperator.LT, CustomAppValueOperator.LTE,
                    CustomAppValueOperator.IS_EMPTY, CustomAppValueOperator.IS_NOT_EMPTY);
    private static final Set<CustomAppValueOperator> REFERENCE_OPS =
            EnumSet.of(CustomAppValueOperator.EQ, CustomAppValueOperator.NOT_EQ,
                    CustomAppValueOperator.IS_EMPTY, CustomAppValueOperator.IS_NOT_EMPTY);

    /** A filter clause resolved against a concrete property of the app. */
    public record ValidatedFilter(CustomAppProperty property, CustomAppValueOperator operator, JsonNode value) {
    }

    /** A sort clause; {@code property == null} means the reserved {@code createdAt} key. */
    public record ValidatedSort(CustomAppProperty property, boolean descending) {
    }

    public List<ValidatedFilter> validateFilters(List<CustomAppValueFilterCriteria> filters,
                                                 Map<UUID, CustomAppProperty> properties, ErrorCode errorCode) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        List<ValidatedFilter> validated = new ArrayList<>(filters.size());
        for (CustomAppValueFilterCriteria criteria : filters) {
            CustomAppProperty property = resolveProperty(criteria.propertyId(), properties, errorCode);
            if (!allowedOperators(property.getType()).contains(criteria.operator())) {
                throw invalid(errorCode, "Operator " + criteria.operator() + " is not supported for property '"
                        + property.getName() + "' of type " + property.getType());
            }
            boolean needsValue = criteria.operator() != CustomAppValueOperator.IS_EMPTY
                    && criteria.operator() != CustomAppValueOperator.IS_NOT_EMPTY;
            if (needsValue) {
                if (criteria.value() == null || criteria.value().isNull()) {
                    throw invalid(errorCode, "Operator " + criteria.operator() + " requires a value");
                }
                validateValueShape(property, criteria.operator(), criteria.value(), errorCode);
            } else if (criteria.value() != null && !criteria.value().isNull()) {
                throw invalid(errorCode, "Operator " + criteria.operator() + " takes no value");
            }
            validated.add(new ValidatedFilter(property, criteria.operator(), criteria.value()));
        }
        return validated;
    }

    public List<ValidatedSort> validateSorts(List<CustomAppValueSortCriteria> sorts,
                                             Map<UUID, CustomAppProperty> properties, ErrorCode errorCode) {
        if (sorts == null || sorts.isEmpty()) {
            return List.of();
        }
        List<ValidatedSort> validated = new ArrayList<>(sorts.size());
        for (CustomAppValueSortCriteria criteria : sorts) {
            if (CREATED_AT_SORT.equals(criteria.propertyId())) {
                validated.add(new ValidatedSort(null, criteria.descending()));
                continue;
            }
            CustomAppProperty property = resolveProperty(criteria.propertyId(), properties, errorCode);
            validated.add(new ValidatedSort(property, criteria.descending()));
        }
        return validated;
    }

    /** Indexes a property list by id — the lookup base for clause resolution. */
    public static Map<UUID, CustomAppProperty> byId(List<CustomAppProperty> properties) {
        Map<UUID, CustomAppProperty> map = new HashMap<>(properties.size());
        for (CustomAppProperty property : properties) {
            map.put(property.getId(), property);
        }
        return map;
    }

    private Set<CustomAppValueOperator> allowedOperators(PropertyType type) {
        return switch (type) {
            case TEXT -> TEXT_OPS;
            case NUMBER, DATE -> COMPARABLE_OPS;
            case SELECT, USER, RELATION -> REFERENCE_OPS;
            case FORMULA -> EnumSet.noneOf(CustomAppValueOperator.class);
        };
    }

    private void validateValueShape(CustomAppProperty property, CustomAppValueOperator operator,
                                    JsonNode value, ErrorCode errorCode) {
        switch (operator) {
            case EQ, NOT_EQ -> {
                if (!value.isTextual() && !value.isNumber()) {
                    throw invalid(errorCode, "Operator " + operator + " takes a scalar (string or number) value");
                }
            }
            case CONTAINS -> {
                if (!value.isTextual()) {
                    throw invalid(errorCode, "Operator CONTAINS takes a string value");
                }
            }
            case GT, GTE, LT, LTE -> {
                switch (property.getType()) {
                    case NUMBER -> {
                        if (!value.isNumber()) {
                            throw invalid(errorCode, "Property '" + property.getName()
                                    + "' is numeric; " + operator + " takes a number");
                        }
                    }
                    case DATE -> {
                        if (!value.isTextual() || !isIsoDate(value.stringValue())) {
                            throw invalid(errorCode, "Property '" + property.getName()
                                    + "' is a date; " + operator + " takes an ISO-8601 date string (YYYY-MM-DD)");
                        }
                    }
                    default -> throw invalid(errorCode,
                            "Operator " + operator + " is not supported for type " + property.getType());
                }
            }
            default -> throw invalid(errorCode, "Unexpected operator: " + operator);
        }
    }

    private CustomAppProperty resolveProperty(String propertyId, Map<UUID, CustomAppProperty> properties, ErrorCode errorCode) {
        UUID id;
        try {
            id = UUID.fromString(propertyId);
        } catch (IllegalArgumentException ex) {
            throw invalid(errorCode, "Unknown property id: '" + propertyId + "'");
        }
        CustomAppProperty property = properties.get(id);
        if (property == null) {
            throw invalid(errorCode, "Property '" + propertyId + "' does not exist in this custom app");
        }
        if (property.getType() == PropertyType.FORMULA) {
            throw invalid(errorCode, "Property '" + property.getName() + "' is a deferred FORMULA and cannot be queried");
        }
        return property;
    }

    private static boolean isIsoDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private BusinessException invalid(ErrorCode errorCode, String message) {
        return new BusinessException(errorCode, message);
    }
}
