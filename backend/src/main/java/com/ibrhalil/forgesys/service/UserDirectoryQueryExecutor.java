package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.UserDirectoryViewResponse;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.User_;
import com.ibrhalil.forgesys.entity.UserAccount_;
import com.ibrhalil.forgesys.entity.UserProfile_;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.projection.ProjectionListQuery;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read side of the flattened user directory (K-49): profile/account joins +
 * role/group counts as ONE Criteria DTO projection ({@link ProjectionListQuery}) —
 * replaced the former {@code @Subselect UserDirectoryView} read model; nothing is
 * hydrated (N+1 impossible). Soft-delete rides the joined entities'
 * soft-delete filter → counts exclude soft-deleted roles/groups.
 * Rationale: docs/CODE_NOTES.md (backend/service → UserDirectoryQueryExecutor).
 */
@Component
public class UserDirectoryQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    /** Subquery counting a plural association's members (LEFT join, 0 when none). */
    static FilterFieldSet.SubqueryExpression countMembers(String association) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Join<?, ?> members = sq.correlate(root).join(association, JoinType.LEFT);
            return sq.select(cb.count(members));
        };
    }

    /** One flat directory query: projection + filter/sort/paging, plus the count query. */
    public Page<UserDirectoryViewResponse> search(@Nullable Specification<User> spec, Pageable pageable) {
        return ProjectionListQuery.execute(entityManager, User.class, UserDirectoryViewResponse.class,
                UserService.FILTER_FIELDS,
                UserDirectoryQueryExecutor::select,
                spec, pageable);
    }

    private static Selection<UserDirectoryViewResponse> select(Root<User> root, CriteriaQuery<UserDirectoryViewResponse> query, CriteriaBuilder cb) {
        return cb.construct(UserDirectoryViewResponse.class,
                root.get(BaseEntity_.ID),
                root.get(User_.USERNAME),
                root.get(User_.EMAIL),
                root.get(User_.EMAIL_VERIFIED),
                root.join(User_.USER_PROFILE, JoinType.LEFT).get(UserProfile_.FIRST_NAME),
                root.join(User_.USER_PROFILE, JoinType.LEFT).get(UserProfile_.LAST_NAME),
                root.join(User_.USER_ACCOUNT, JoinType.LEFT).get(UserAccount_.ENABLED),
                root.join(User_.USER_ACCOUNT, JoinType.LEFT).get(UserAccount_.LOCKED_UNTIL),
                root.join(User_.USER_ACCOUNT, JoinType.LEFT).get(UserAccount_.LAST_LOGIN_AT),
                root.get(AuditEntity_.CREATED_DATE),
                countMembers(User_.ROLES).apply(root, query, cb),
                countMembers(User_.GROUPS).apply(root, query, cb));
    }
}
