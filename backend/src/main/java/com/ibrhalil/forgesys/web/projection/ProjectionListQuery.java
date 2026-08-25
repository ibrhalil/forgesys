package com.ibrhalil.forgesys.web.projection;

import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared Criteria DTO projection executor behind the paged list endpoints (K-49).
 * Runs one content query — a constructor projection ({@code cb.construct}), so rows
 * are DTOs, never managed entities: no hydration overhead, no N+1, no dirty
 * checking — plus one count query with the same predicate. Sort properties are
 * translated through the feature's {@link FilterFieldSet} (wire name → expression),
 * so joined and subquery-derived columns filter, sort and project in the database
 * exactly like direct ones.
 *
 * <p>Flatness rule: the feature's registrations may JOIN only to-one associations
 * (LEFT) and must keep subquery fields scalar — a to-many join would multiply rows
 * and silently break both paging and the count query. Collection data (counts,
 * membership) goes through scalar subqueries / EXISTS predicates, mirroring the
 * former {@code UserDirectoryView} read model per feature.
 *
 * <p>Sort validation ({@code SortGuard}) runs at the controller layer; resolving a
 * non-registered or non-sortable property here throws {@link IllegalArgumentException}
 * as the last line of defense.
 */
public final class ProjectionListQuery {

    private ProjectionListQuery() {
    }

    /** Produces the constructor projection selecting the DTO columns off the root. */
    @FunctionalInterface
    public interface SelectionFactory<E, R> {

        Selection<R> select(Root<E> root, CriteriaQuery<R> query, CriteriaBuilder cb);
    }

    public static <E, R> Page<R> execute(EntityManager em, Class<E> entityClass, Class<R> resultType,
            FilterFieldSet fields, SelectionFactory<E, R> selection, Specification<E> spec, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<R> query = cb.createQuery(resultType);
        Root<E> root = query.from(entityClass);
        query.select(selection.select(root, query, cb));
        applyPredicate(spec, root, query, cb);
        applySort(fields, pageable, root, query, cb);

        TypedQuery<R> typed = em.createQuery(query);
        if (pageable.isPaged()) {
            typed.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        }
        List<R> content = typed.getResultList();

        return new PageImpl<>(content, pageable, count(em, entityClass, spec, cb));
    }

    private static <E> long count(EntityManager em, Class<E> entityClass, Specification<E> spec, CriteriaBuilder cb) {
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<E> countRoot = countQuery.from(entityClass);
        countQuery.select(cb.count(countRoot));
        applyPredicate(spec, countRoot, countQuery, cb);
        return em.createQuery(countQuery).getSingleResult();
    }

    private static <E> void applyPredicate(Specification<E> spec, Root<E> root, CriteriaQuery<?> query,
            CriteriaBuilder cb) {
        if (spec == null) {
            return;
        }
        Predicate predicate = spec.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    private static <E> void applySort(FilterFieldSet fields, Pageable pageable, Root<E> root,
            CriteriaQuery<?> query, CriteriaBuilder cb) {
        Sort sort = pageable.getSort();
        if (!sort.isSorted()) {
            return;
        }
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            FilterFieldSet.RegisteredField field = fields.get(order.getProperty());
            if (field == null || !field.sortable()) {
                throw new IllegalArgumentException("Unsupported sort property: " + order.getProperty());
            }
            Expression<?> expression = fields.resolve(order.getProperty(), root, query, cb);
            orders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
        }
        query.orderBy(orders);
    }
}
