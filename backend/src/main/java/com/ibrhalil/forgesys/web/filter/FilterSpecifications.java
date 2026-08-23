package com.ibrhalil.forgesys.web.filter;

import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Translates a global {@code q} plus {@link FilterCriteria} clauses into a combined
 * {@link Specification} (filters AND-joined; {@code q} OR-CONTAINS over the registered
 * searchable fields). All validation happens here, eagerly at build time — an invalid
 * request fails with 400 {@code validation_error} before any query runs, never as a
 * mid-execution 500. Value parsing goes through {@link FilterValueParser}.
 */
public final class FilterSpecifications {

    /** Hard cap on values per IN/NOT_IN clause — bounds the generated SQL. */
    static final int MAX_IN_VALUES = 100;

    private FilterSpecifications() {
    }

    public static <T> Specification<T> from(FilterFieldSet fields, String q, List<FilterCriteria> filters) {
        List<Specification<T>> parts = new ArrayList<>();
        if (filters != null) {
            for (FilterCriteria criteria : filters) {
                parts.add(criteria(fields, criteria));
            }
        }
        if (StringUtils.hasText(q)) {
            parts.add(search(fields, q.trim()));
        }
        return parts.isEmpty() ? Specification.unrestricted() : Specification.allOf(parts);
    }

    // ── q: OR over searchable fields, case-insensitive containment ──

    private static <T> Specification<T> search(FilterFieldSet fields, String q) {
        List<FilterFieldSet.RegisteredField> searchable = fields.searchableFields();
        if (searchable.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Search ('q') is not supported on this resource");
        }
        String needle = "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> likes = new ArrayList<>(searchable.size());
            for (FilterFieldSet.RegisteredField field : searchable) {
                likes.add(likeIgnoreCase(cb, stringPath(root.get(field.name())), needle));
            }
            return cb.or(likes.toArray(Predicate[]::new));
        };
    }

    // ── single clause ──

    private static <T> Specification<T> criteria(FilterFieldSet fields, FilterCriteria criteria) {
        FilterFieldSet.RegisteredField field = fields.get(criteria.field());
        if (field == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Unknown filter field: '" + criteria.field() + "'. Allowed: " + fields.names().stream().sorted().toList());
        }
        FilterOperator operator = criteria.operator();
        if (!field.type().supports(operator)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Operator '" + operator + "' is not supported for field '" + field.name()
                            + "' (" + field.type() + ")");
        }
        List<String> raw = criteria.values() == null ? List.of() : criteria.values();
        validateArity(field, operator, raw);
        List<Object> values = raw.stream().map(v -> FilterValueParser.parse(field, v)).toList();

        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            Path<?> path = root.get(field.name());
            return switch (operator) {
                case EQ -> cb.equal(path, values.get(0));
                case NOT_EQ -> cb.notEqual(path, values.get(0));
                case IN -> path.in(values);
                case NOT_IN -> cb.not(path.in(values));
                case CONTAINS -> likeIgnoreCase(cb, stringPath(path),
                        "%" + escapeLike(lower(values.get(0))) + "%");
                case STARTS_WITH -> likeIgnoreCase(cb, stringPath(path),
                        escapeLike(lower(values.get(0))) + "%");
                case ENDS_WITH -> likeIgnoreCase(cb, stringPath(path),
                        "%" + escapeLike(lower(values.get(0))));
                case GT, GTE, LT, LTE, BETWEEN -> comparablePredicate(cb, path, operator, values);
                case IS_NULL -> cb.isNull(path);
                case IS_NOT_NULL -> cb.isNotNull(path);
            };
        };
    }

    private static void validateArity(FilterFieldSet.RegisteredField field, FilterOperator operator, List<String> values) {
        switch (operator) {
            case EQ, NOT_EQ, GT, GTE, LT, LTE, CONTAINS, STARTS_WITH, ENDS_WITH -> requireValues(field, operator, values, 1, 1);
            case BETWEEN -> requireValues(field, operator, values, 2, 2);
            case IN, NOT_IN -> requireValues(field, operator, values, 1, MAX_IN_VALUES);
            case IS_NULL, IS_NOT_NULL -> requireValues(field, operator, values, 0, 0);
        }
    }

    private static void requireValues(FilterFieldSet.RegisteredField field, FilterOperator operator,
                                      List<String> values, int min, int max) {
        if (values.size() < min || values.size() > max) {
            String expected = min == max
                    ? "exactly " + min
                    : min + " to " + max;
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Filter '" + field.name() + "' with operator '" + operator + "' requires " + expected
                            + " value(s), got " + values.size());
        }
    }

    // ── criteria helpers (LIKE escaping, Comparable casts) ──

    /**
     * Case-insensitive LIKE: the column is lowered by the DB and the pattern was
     * lowered with {@code Locale.ROOT} in Java. This assumes the DB lower() folds
     * ASCII the same way Locale.ROOT does — true for the en-locale PostgreSQL
     * instances and for the H2 test JVM (whose default locale is pinned to English
     * via the surefire {@code user.language=en} argLine; a Turkish-locale JVM would
     * otherwise turn {@code lower('I')} into {@code 'ı'} and break case-insensitive
     * search on H2).
     */
    private static Predicate likeIgnoreCase(CriteriaBuilder cb, Expression<String> path, String pattern) {
        return cb.like(cb.lower(path), pattern, '\\');
    }

    private static String lower(Object value) {
        return value.toString().toLowerCase(Locale.ROOT);
    }

    /** Escapes LIKE metacharacters ({@code % _ \}) so user input matches literally. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Expression<String> stringPath(Path<?> path) {
        @SuppressWarnings("unchecked")
        Expression<String> cast = (Expression<String>) path;
        return cast;
    }

    /**
     * Comparison predicates for {@code Comparable}-typed fields (TEMPORAL today). The
     * unchecked casts pin one {@code Y} for the whole expression/value pair, which
     * resolves the {@code CriteriaBuilder} overload ambiguity — values were parsed to
     * the field's declared type by {@link FilterValueParser}, so erasure keeps them
     * type-correct at runtime.
     */
    @SuppressWarnings("unchecked")
    private static <Y extends Comparable<? super Y>> Predicate comparablePredicate(
            CriteriaBuilder cb, Path<?> path, FilterOperator operator, List<Object> values) {
        Expression<Y> expression = (Expression<Y>) path;
        return switch (operator) {
            case GT -> cb.greaterThan(expression, (Y) values.get(0));
            case GTE -> cb.greaterThanOrEqualTo(expression, (Y) values.get(0));
            case LT -> cb.lessThan(expression, (Y) values.get(0));
            case LTE -> cb.lessThanOrEqualTo(expression, (Y) values.get(0));
            case BETWEEN -> cb.between(expression, (Y) values.get(0), (Y) values.get(1));
            default -> throw new IllegalStateException("Not a comparable operator: " + operator);
        };
    }
}
