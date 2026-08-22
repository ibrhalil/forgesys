package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AppValueFilterCriteria;
import com.ibrhalil.forgesys.dto.AppValueOperator;
import com.ibrhalil.forgesys.dto.AppValueSortCriteria;
import com.ibrhalil.forgesys.entity.AppProperty;
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
 * Shared validation for property-value filter/sort clauses (K-15 / Epic 3.0.B) — used
 * by both record search ({@code AppRecordSearchRequest}) and saved view configs
 * ({@code AppViewConfigDto}), so the two stay in lockstep. Every clause must resolve
 * against the app's property set: property existence, operator-vs-type support and
 * value shape are checked <em>eagerly</em>, before any query is built — an invalid
 * request is a 400, never a mid-query 500. SQL built downstream references only
 * validated UUIDs and enum-derived fragments, keeping the JSONB query injection-free.
 */
@Component
public class AppQueryValidator {

    /** Reserved sort key addressing record creation time instead of a property. */
    public static final String CREATED_AT_SORT = "createdAt";

    private static final Set<AppValueOperator> TEXT_OPS =
            EnumSet.of(AppValueOperator.EQ, AppValueOperator.NOT_EQ, AppValueOperator.CONTAINS,
                    AppValueOperator.IS_EMPTY, AppValueOperator.IS_NOT_EMPTY);
    private static final Set<AppValueOperator> COMPARABLE_OPS =
            EnumSet.of(AppValueOperator.EQ, AppValueOperator.NOT_EQ, AppValueOperator.GT,
                    AppValueOperator.GTE, AppValueOperator.LT, AppValueOperator.LTE,
                    AppValueOperator.IS_EMPTY, AppValueOperator.IS_NOT_EMPTY);
    private static final Set<AppValueOperator> REFERENCE_OPS =
            EnumSet.of(AppValueOperator.EQ, AppValueOperator.NOT_EQ,
                    AppValueOperator.IS_EMPTY, AppValueOperator.IS_NOT_EMPTY);

    /** A filter clause resolved against a concrete property of the app. */
    public record ValidatedFilter(AppProperty property, AppValueOperator operator, JsonNode value) {
    }

    /** A sort clause; {@code property == null} means the reserved {@code createdAt} key. */
    public record ValidatedSort(AppProperty property, boolean descending) {
    }

    public List<ValidatedFilter> validateFilters(List<AppValueFilterCriteria> filters,
                                                 Map<UUID, AppProperty> properties, ErrorCode errorCode) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        List<ValidatedFilter> validated = new ArrayList<>(filters.size());
        for (AppValueFilterCriteria criteria : filters) {
            AppProperty property = resolveProperty(criteria.propertyId(), properties, errorCode);
            if (!allowedOperators(property.getType()).contains(criteria.operator())) {
                throw invalid(errorCode, "Operator " + criteria.operator() + " is not supported for property '"
                        + property.getName() + "' of type " + property.getType());
            }
            boolean needsValue = criteria.operator() != AppValueOperator.IS_EMPTY
                    && criteria.operator() != AppValueOperator.IS_NOT_EMPTY;
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

    public List<ValidatedSort> validateSorts(List<AppValueSortCriteria> sorts,
                                             Map<UUID, AppProperty> properties, ErrorCode errorCode) {
        if (sorts == null || sorts.isEmpty()) {
            return List.of();
        }
        List<ValidatedSort> validated = new ArrayList<>(sorts.size());
        for (AppValueSortCriteria criteria : sorts) {
            if (CREATED_AT_SORT.equals(criteria.propertyId())) {
                validated.add(new ValidatedSort(null, criteria.descending()));
                continue;
            }
            AppProperty property = resolveProperty(criteria.propertyId(), properties, errorCode);
            validated.add(new ValidatedSort(property, criteria.descending()));
        }
        return validated;
    }

    /** Indexes a property list by id — the lookup base for clause resolution. */
    public static Map<UUID, AppProperty> byId(List<AppProperty> properties) {
        Map<UUID, AppProperty> map = new HashMap<>(properties.size());
        for (AppProperty property : properties) {
            map.put(property.getId(), property);
        }
        return map;
    }

    private Set<AppValueOperator> allowedOperators(PropertyType type) {
        return switch (type) {
            case TEXT -> TEXT_OPS;
            case NUMBER, DATE -> COMPARABLE_OPS;
            case SELECT, USER, RELATION -> REFERENCE_OPS;
            case FORMULA -> EnumSet.noneOf(AppValueOperator.class);
        };
    }

    private void validateValueShape(AppProperty property, AppValueOperator operator,
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

    private AppProperty resolveProperty(String propertyId, Map<UUID, AppProperty> properties, ErrorCode errorCode) {
        UUID id;
        try {
            id = UUID.fromString(propertyId);
        } catch (IllegalArgumentException ex) {
            throw invalid(errorCode, "Unknown property id: '" + propertyId + "'");
        }
        AppProperty property = properties.get(id);
        if (property == null) {
            throw invalid(errorCode, "Property '" + propertyId + "' does not exist in this app");
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
