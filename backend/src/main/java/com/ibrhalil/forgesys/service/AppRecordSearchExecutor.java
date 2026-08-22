package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AppValueOperator;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.service.AppQueryValidator.ValidatedFilter;
import com.ibrhalil.forgesys.service.AppQueryValidator.ValidatedSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Native PostgreSQL JSONB search over {@code t_app_record_values} (K-15 / Epic 3.0.B).
 * Executes filter/sort clauses already validated by {@link AppQueryValidator} — the SQL
 * is assembled exclusively from enum-derived fragments and explicitly numbered
 * positional parameters, so user input never becomes SQL text (the filter/sort criteria
 * ARE the query DSL — the deliberate 3.0.B spike outcome: no expression language, no
 * injection surface).
 *
 * <p>PostgreSQL-only ({@code @>} containment, {@code #>>} accessor, {@code ILIKE},
 * {@code ::numeric} casts — GIN-index backed via {@code jsonb_path_ops}); plain record
 * CRUD is portable and covered by H2 tests, this path is verified by the gated
 * {@code AppBuilderIT} against real PostgreSQL. Runs on the tenant's
 * {@code search_path} through the multi-tenant {@link EntityManager}.
 *
 * <p>Empty-cell semantics: a record with no value row for a property matches only
 * IS_EMPTY / IS_NOT_EMPTY — value operators ({@code EQ}, {@code GT}, ...) implicitly
 * require the cell to be non-empty.
 */
@Repository
public class AppRecordSearchExecutor {

    private static final String VALUE_TABLE = "t_app_record_values";

    @PersistenceContext
    private EntityManager entityManager;

    public Page<UUID> search(UUID appId, List<ValidatedFilter> filters, List<ValidatedSort> sorts,
                             Pageable pageable) {
        // Fragments are appended strictly in final-SQL appearance order, with an
        // explicit running parameter index (?1, ?2, ...) matching the bind list.
        ParamSql where = new ParamSql();
        where.append("r.app_id = ?", appId);
        where.append("r.is_deleted = false");
        for (ValidatedFilter filter : filters) {
            appendFilter(where, filter);
        }
        int whereParamCount = where.params.size();

        List<String> orderParts = new ArrayList<>();
        ParamSql order = new ParamSql(whereParamCount);
        for (ValidatedSort sort : sorts) {
            if (sort.property() == null) {
                orderParts.add("r.created_at " + (sort.descending() ? "DESC" : "ASC"));
            } else {
                String accessor = sort.property().getType() == PropertyType.NUMBER
                        ? "NULLIF(v.value #>> '{}', '')::numeric"
                        : "v.value #>> '{}'";
                String marker = order.next();
                order.params.add(sort.property().getId());
                orderParts.add("(SELECT " + accessor + " FROM " + VALUE_TABLE + " v"
                        + " WHERE v.record_id = r.id AND v.property_id = " + marker + ")"
                        + (sort.descending() ? " DESC" : " ASC"));
            }
        }
        orderParts.add("r.created_at DESC"); // stable tiebreaker for deterministic paging

        String whereSql = String.join(" AND ", where.fragments);
        String countSql = "SELECT count(*) FROM t_app_records r WHERE " + whereSql;

        String listSql = "SELECT r.id FROM t_app_records r WHERE " + whereSql
                + " ORDER BY " + String.join(", ", orderParts)
                + " LIMIT " + order.next() + " OFFSET " + order.next();
        order.params.add(pageable.getPageSize());
        order.params.add((int) pageable.getOffset());

        long total = ((Number) bind(countSql, where.params).getSingleResult()).longValue();
        List<Object> rows = bind(listSql, merge(where.params, order.params)).getResultList();
        List<UUID> ids = rows.stream().map(AppRecordSearchExecutor::asUuid).toList();
        return new PageImpl<>(ids, pageable, total);
    }

    private void appendFilter(ParamSql sql, ValidatedFilter filter) {
        UUID propertyId = filter.property().getId();
        switch (filter.operator()) {
            case IS_EMPTY, IS_NOT_EMPTY -> {
                String marker = sql.next();
                sql.params.add(propertyId);
                String exists = "EXISTS (SELECT 1 FROM " + VALUE_TABLE + " v"
                        + " WHERE v.record_id = r.id AND v.property_id = " + marker + ")";
                sql.fragments.add(filter.operator() == AppValueOperator.IS_EMPTY ? "NOT " + exists : exists);
            }
            case EQ, NOT_EQ -> {
                String propertyMarker = sql.next();
                String valueMarker = sql.next();
                sql.params.add(propertyId);
                sql.params.add(filter.value().toString());
                String exists = "EXISTS (SELECT 1 FROM " + VALUE_TABLE + " v"
                        + " WHERE v.record_id = r.id AND v.property_id = " + propertyMarker
                        + " AND v.value @> CAST(" + valueMarker + " AS jsonb))";
                sql.fragments.add(filter.operator() == AppValueOperator.EQ ? exists : "NOT " + exists);
            }
            case CONTAINS -> {
                String propertyMarker = sql.next();
                String valueMarker = sql.next();
                sql.params.add(propertyId);
                sql.params.add("%" + filter.value().stringValue() + "%");
                sql.fragments.add("EXISTS (SELECT 1 FROM " + VALUE_TABLE + " v"
                        + " WHERE v.record_id = r.id AND v.property_id = " + propertyMarker
                        + " AND v.value #>> '{}' ILIKE " + valueMarker + ")");
            }
            // GT/GTE/LT/LTE — NUMBER compares numerically, DATE as ISO text (lexicographic).
            case GT, GTE, LT, LTE -> {
                boolean numeric = filter.property().getType() == PropertyType.NUMBER;
                String propertyMarker = sql.next();
                String valueMarker = sql.next();
                sql.params.add(propertyId);
                sql.params.add(numeric ? filter.value().decimalValue() : filter.value().stringValue());
                String left = numeric ? "NULLIF(v.value #>> '{}', '')::numeric" : "v.value #>> '{}'";
                String right = numeric ? "CAST(" + valueMarker + " AS numeric)" : valueMarker;
                sql.fragments.add("EXISTS (SELECT 1 FROM " + VALUE_TABLE + " v"
                        + " WHERE v.record_id = r.id AND v.property_id = " + propertyMarker
                        + " AND " + left + " " + sqlComparator(filter.operator()) + " " + right + ")");
            }
        }
    }

    private String sqlComparator(AppValueOperator operator) {
        return switch (operator) {
            case GT -> ">";
            case GTE -> ">=";
            case LT -> "<";
            case LTE -> "<=";
            default -> throw new IllegalArgumentException("Not a comparator: " + operator);
        };
    }

    private Query bind(String sql, List<Object> params) {
        Query query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        return query;
    }

    private static List<Object> merge(List<Object> first, List<Object> second) {
        List<Object> all = new ArrayList<>(first.size() + second.size());
        all.addAll(first);
        all.addAll(second);
        return all;
    }

    private static UUID asUuid(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        return UUID.fromString(String.valueOf(value));
    }

    /** Running fragment + positional-parameter builder (explicit {@code ?N} markers). */
    private static final class ParamSql {
        private final List<String> fragments = new ArrayList<>();
        private final List<Object> params = new ArrayList<>();
        private int index;

        ParamSql() {
            this(0);
        }

        ParamSql(int startIndex) {
            this.index = startIndex;
        }

        /** Appends a parameter-less fragment. */
        private void append(String fragment) {
            fragments.add(fragment);
        }

        /** Appends a fragment referencing one new parameter and binds it. */
        private void append(String fragment, Object param) {
            fragments.add(fragment.replace("?", "?" + ++index));
            params.add(param);
        }

        /** Reserves the next marker — caller MUST add the bound value to {@link #params}. */
        private String next() {
            return "?" + ++index;
        }
    }
}
