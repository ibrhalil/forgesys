package com.ibrhalil.forgesys.web.filter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

/**
 * Per-feature whitelist of the filterable/sortable attributes reachable from the
 * wire (K-49); doubles as the sort whitelist for {@code SortGuard.require}. Wire
 * names stay flat; only to-one joins and scalar subqueries are registrable.
 * rationale: docs/CODE_NOTES.md (backend/web → FilterFieldSet)
 */
public final class FilterFieldSet {

    /** How a registered field resolves to a query expression. */
    public enum FieldKind {
        DIRECT, JOINED, SUBQUERY, MEMBERSHIP
    }

    /** Subquery-derived scalar bound to the root (counts, FK name-resolution) — used in WHERE, ORDER BY and SELECT. */
    @FunctionalInterface
    public interface SubqueryExpression {

        Expression<?> apply(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb);
    }

    /**
     * Membership config: {@link #direct} correlates a plural association of the root;
     * {@link #inverse} is for join tables owned by the other side (e.g. group members
     * via {@code User.groups}).
     */
    public record Membership(
            String rootAssociation,
            String memberIdAttribute,
            Class<?> inverseEntity,
            String inverseAssociation,
            String inverseLinkIdAttribute,
            String rootIdAttribute) {

        static Membership direct(String rootAssociation, String memberIdAttribute) {
            return new Membership(rootAssociation, memberIdAttribute, null, null, null, null);
        }

        static Membership inverse(Class<?> inverseEntity, String inverseAssociation,
                String inverseLinkIdAttribute, String rootIdAttribute) {
            return new Membership(null, "id", inverseEntity, inverseAssociation, inverseLinkIdAttribute,
                    rootIdAttribute);
        }

        boolean inverse() {
            return inverseEntity != null;
        }
    }

    /** One registered field: wire name, coarse type, kind and its resolution data. */
    public record RegisteredField(
            String name,
            FilterFieldType type,
            Class<?> javaType,
            boolean searchable,
            FieldKind kind,
            boolean sortable,
            String joinAttribute,
            String leafAttribute,
            SubqueryExpression expression,
            Membership membership) {

        static RegisteredField direct(String name, FilterFieldType type, boolean searchable) {
            return direct(name, type, type.javaType(), searchable);
        }

        static RegisteredField direct(String name, FilterFieldType type, Class<?> javaType, boolean searchable) {
            return new RegisteredField(name, type, javaType, searchable, FieldKind.DIRECT, true,
                    null, null, null, null);
        }

        static RegisteredField joined(String name, FilterFieldType type, boolean searchable,
                String joinAttribute, String leafAttribute) {
            return new RegisteredField(name, type, type.javaType(), searchable, FieldKind.JOINED, true,
                    joinAttribute, leafAttribute, null, null);
        }

        static RegisteredField subquery(String name, FilterFieldType type, boolean searchable,
                SubqueryExpression expression) {
            return new RegisteredField(name, type, type.javaType(), searchable, FieldKind.SUBQUERY, true,
                    null, null, expression, null);
        }

        static RegisteredField membership(String name, Membership membership) {
            return new RegisteredField(name, FilterFieldType.UUID, UUID.class, false, FieldKind.MEMBERSHIP, false,
                    null, null, null, membership);
        }

        /** Operator support is kind-aware: MEMBERSHIP accepts only the set operators. */
        public boolean supports(FilterOperator operator) {
            if (kind == FieldKind.MEMBERSHIP) {
                return operator == FilterOperator.IN || operator == FilterOperator.NOT_IN
                        || operator == FilterOperator.IS_NULL || operator == FilterOperator.IS_NOT_NULL;
            }
            return type.supports(operator);
        }
    }

    private final Map<String, RegisteredField> fields;

    private FilterFieldSet(Map<String, RegisteredField> fields) {
        this.fields = Collections.unmodifiableMap(fields);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean contains(String name) {
        return fields.containsKey(name);
    }

    /** The registered field, or {@code null} for unknown names (callers map that to 400). */
    public RegisteredField get(String name) {
        return fields.get(name);
    }

    public Set<String> names() {
        return fields.keySet();
    }

    /** Names registered as sortable — the sort whitelist surface. */
    public List<String> sortableNames() {
        return fields.values().stream().filter(RegisteredField::sortable)
                .map(RegisteredField::name).sorted().toList();
    }

    /** Fields participating in the global {@code q} search (OR-CONTAINS over each). */
    public List<RegisteredField> searchableFields() {
        return fields.values().stream().filter(RegisteredField::searchable).toList();
    }

    /** Resolves a field to its expression (direct attribute, JOIN leaf, or subquery scalar); MEMBERSHIP resolves to EXISTS predicates instead. */
    public <T> Expression<?> resolve(String name, Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        RegisteredField field = fields.get(name);
        if (field == null) {
            throw new IllegalArgumentException("Unknown field: " + name);
        }
        return switch (field.kind()) {
            case DIRECT -> root.get(field.name());
            case JOINED -> root.join(field.joinAttribute(), JoinType.LEFT).get(field.leafAttribute());
            case SUBQUERY -> field.expression().apply(root, query, cb);
            case MEMBERSHIP -> throw new IllegalArgumentException(
                    "Membership fields resolve to EXISTS predicates, not expressions: " + name);
        };
    }

    public static final class Builder {

        private final Map<String, RegisteredField> fields = new LinkedHashMap<>();

        /** Registers a direct attribute of a self-describing type (STRING, UUID, ...). */
        public Builder field(String name, FilterFieldType type, boolean searchable) {
            return register(RegisteredField.direct(name, type, searchable));
        }

        /** Direct enum-typed field; {@code enumType} parses wire values (unknown names → 400). */
        public Builder enumField(String name, Class<?> enumType, boolean searchable) {
            return register(RegisteredField.direct(name, FilterFieldType.ENUM, enumType, searchable));
        }

        /** Field on a to-one association of the root (LEFT JOIN + leaf); to-many is not registrable by design. */
        public Builder joinedField(String name, FilterFieldType type, boolean searchable,
                String joinAttribute, String leafAttribute) {
            return register(RegisteredField.joined(name, type, searchable, joinAttribute, leafAttribute));
        }

        /** Registers a subquery-derived scalar field (counts, referenced names). */
        public Builder subqueryField(String name, FilterFieldType type, boolean searchable,
                SubqueryExpression expression) {
            return register(RegisteredField.subquery(name, type, searchable, expression));
        }

        /** Membership filter over a root plural association ({@code IN} = contains, {@code IS_NULL} = empty); not sortable, not q-searchable. */
        public Builder membershipField(String name, String memberAssociation, String memberIdAttribute) {
            return register(RegisteredField.membership(name, Membership.direct(memberAssociation, memberIdAttribute)));
        }

        /** Inverse membership for join tables owned by the other side — EXISTS starts from {@code inverseEntity} and correlates back by link id. */
        public Builder inverseMembershipField(String name, Class<?> inverseEntity, String inverseAssociation,
                String inverseLinkIdAttribute, String rootIdAttribute) {
            return register(RegisteredField.membership(name,
                    Membership.inverse(inverseEntity, inverseAssociation, inverseLinkIdAttribute, rootIdAttribute)));
        }

        private Builder register(RegisteredField field) {
            if (field.name() == null || field.name().isBlank()) {
                throw new IllegalArgumentException("Field name must not be blank");
            }
            if (field.searchable() && field.type() != FilterFieldType.STRING) {
                throw new IllegalArgumentException("Searchable fields must be STRING-typed: '" + field.name() + "'");
            }
            fields.put(field.name(), field);
            return this;
        }

        public FilterFieldSet build() {
            return new FilterFieldSet(fields);
        }
    }
}
