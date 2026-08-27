package com.ibrhalil.forgesys.web.filter;

import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the combined {@link Specification} from a global {@code q} (optionally
 * narrowed to {@code qFields}) plus {@link FilterCriteria} clauses (filters
 * AND-joined, {@code q} OR-CONTAINS over searchable fields). All validation is
 * eager at build time — invalid requests fail 400 {@code validation_error} before
 * any query runs.
 * rationale: docs/CODE_NOTES.md (backend/web → FilterSpecifications)
 */
public final class FilterSpecifications {

    /** Hard cap on values per IN/NOT_IN clause — bounds the generated SQL. */
    static final int MAX_IN_VALUES = 100;

    private FilterSpecifications() {
    }

    public static <T> Specification<T> from(FilterFieldSet fields, String q, List<FilterCriteria> filters) {
        return from(fields, q, null, filters);
    }

    /**
     * @param qFields optional subset of searchable field names the {@code q} term is
     *                matched against; unknown or non-searchable names fail with 400
     */
    public static <T> Specification<T> from(FilterFieldSet fields, String q, List<String> qFields,
            List<FilterCriteria> filters) {
        List<Specification<T>> parts = new ArrayList<>();
        if (filters != null) {
            for (FilterCriteria criteria : filters) {
                parts.add(criteria(fields, criteria));
            }
        }
        if (StringUtils.hasText(q)) {
            parts.add(search(fields, qFields, q.trim()));
        }
        return parts.isEmpty() ? Specification.unrestricted() : Specification.allOf(parts);
    }

    private static <T> Specification<T> search(FilterFieldSet fields, List<String> qFields, String q) {
        List<FilterFieldSet.RegisteredField> searchable = fields.searchableFields();
        if (qFields != null && !qFields.isEmpty()) {
            searchable = selectedSearchable(fields, searchable, qFields);
        }
        if (searchable.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Search ('q') is not supported on this resource");
        }
        List<FilterFieldSet.RegisteredField> targets = searchable;
        String needle = "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> likes = new ArrayList<>(targets.size());
            for (FilterFieldSet.RegisteredField field : targets) {
                Expression<?> expression = fields.resolve(field.name(), root, query, cb);
                likes.add(likeIgnoreCase(cb, stringExpression(expression), needle));
            }
            return cb.or(likes.toArray(Predicate[]::new));
        };
    }

    private static List<FilterFieldSet.RegisteredField> selectedSearchable(
            FilterFieldSet fields, List<FilterFieldSet.RegisteredField> searchable, List<String> qFields) {
        List<FilterFieldSet.RegisteredField> selected = new ArrayList<>(qFields.size());
        List<String> allowed = searchable.stream().map(FilterFieldSet.RegisteredField::name).sorted().toList();
        for (String name : qFields) {
            FilterFieldSet.RegisteredField field = fields.get(name);
            if (field == null || !field.searchable()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Unknown or non-searchable search field: '" + name + "'. Allowed: " + allowed);
            }
            selected.add(field);
        }
        return selected;
    }

    private static <T> Specification<T> criteria(FilterFieldSet fields, FilterCriteria criteria) {
        FilterFieldSet.RegisteredField field = fields.get(criteria.field());
        if (field == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Unknown filter field: '" + criteria.field() + "'. Allowed: " + fields.names().stream().sorted().toList());
        }
        FilterOperator operator = criteria.operator();
        if (!field.supports(operator)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Operator '" + operator + "' is not supported for field '" + field.name() + "' ("
                            + (field.kind() == FilterFieldSet.FieldKind.MEMBERSHIP
                            ? "MEMBERSHIP — supported: IN, NOT_IN, IS_NULL, IS_NOT_NULL"
                            : field.type().toString()) + ")");
        }
        List<String> raw = criteria.values() == null ? List.of() : criteria.values();
        validateArity(field, operator, raw);
        List<Object> values = raw.stream().map(v -> FilterValueParser.parse(field, v)).toList();

        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (field.kind() == FilterFieldSet.FieldKind.MEMBERSHIP) {
                return membershipPredicate(field, operator, values, root, query, cb);
            }
            Expression<?> path = fields.resolve(field.name(), root, query, cb);
            return switch (operator) {
                case EQ -> cb.equal(path, values.get(0));
                case NOT_EQ -> cb.notEqual(path, values.get(0));
                case IN -> path.in(values);
                case NOT_IN -> cb.not(path.in(values));
                case CONTAINS -> likeIgnoreCase(cb, stringExpression(path),
                        "%" + escapeLike(lower(values.get(0))) + "%");
                case STARTS_WITH -> likeIgnoreCase(cb, stringExpression(path),
                        escapeLike(lower(values.get(0))) + "%");
                case ENDS_WITH -> likeIgnoreCase(cb, stringExpression(path),
                        "%" + escapeLike(lower(values.get(0))));
                case GT, GTE, LT, LTE, BETWEEN -> comparablePredicate(cb, path, operator, values);
                case IS_NULL -> cb.isNull(path);
                case IS_NOT_NULL -> cb.isNotNull(path);
            };
        };
    }

    /**
     * Correlated EXISTS: {@code IN}/{@code NOT_IN} restrict to member ids,
     * {@code IS_NULL}/{@code IS_NOT_NULL} test emptiness. Correlated joins apply the
     * member entity's soft-delete filter (soft-deleted members excluded).
     */
    private static Predicate membershipPredicate(FilterFieldSet.RegisteredField field, FilterOperator operator,
            List<Object> values, Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        FilterFieldSet.Membership membership = field.membership();
        boolean restrictToValues = operator == FilterOperator.IN || operator == FilterOperator.NOT_IN;
        Subquery<Integer> memberExists = query.subquery(Integer.class);
        memberExists.select(cb.literal(1));
        if (!membership.inverse()) {
            Join<?, ?> member = memberExists.correlate(root).join(membership.rootAssociation());
            if (restrictToValues) {
                memberExists.where(member.get(membership.memberIdAttribute()).in(values));
            }
        } else {
            Root<?> member = memberExists.from(membership.inverseEntity());
            Join<?, ?> link = member.join(membership.inverseAssociation());
            jakarta.persistence.criteria.Predicate correlation = cb.equal(
                    link.get(membership.inverseLinkIdAttribute()), root.get(membership.rootIdAttribute()));
            memberExists.where(restrictToValues
                    ? cb.and(correlation, member.get(membership.memberIdAttribute()).in(values))
                    : correlation);
        }
        return switch (operator) {
            case IN, IS_NOT_NULL -> cb.exists(memberExists);
            case NOT_IN, IS_NULL -> cb.not(cb.exists(memberExists));
            default -> throw new IllegalStateException("Not a membership operator: " + operator);
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

    /**
     * Case-insensitive LIKE: column lowered by the DB, pattern lowered with
     * {@code Locale.ROOT} — assumes the DB lower() folds ASCII identically
     * (en-locale PG; H2 test JVM pinned via surefire {@code user.language=en}).
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

    private static Expression<String> stringExpression(Expression<?> expression) {
        @SuppressWarnings("unchecked")
        Expression<String> cast = (Expression<String>) expression;
        return cast;
    }

    /** Unchecked casts pin one {@code Y} per expression/value pair — resolves the {@code CriteriaBuilder} overload ambiguity. */
    @SuppressWarnings("unchecked")
    private static <Y extends Comparable<? super Y>> Predicate comparablePredicate(
            CriteriaBuilder cb, Expression<?> path, FilterOperator operator, List<Object> values) {
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
