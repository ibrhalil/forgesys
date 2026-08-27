package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.GroupResponse;
import com.ibrhalil.forgesys.dto.RoleSummary;
import com.ibrhalil.forgesys.dto.UserSummary;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Group_;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.User_;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.projection.ProjectionListQuery;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read side of the group list (K-49): one flat Criteria DTO projection + TWO batch
 * queries for the page's role/member summaries — a fixed 3-query page (formerly 2N+1).
 * Counts exclude soft-deleted roles/users (entity-path soft-delete filter).
 * Rationale: docs/CODE_NOTES.md (backend/service → GroupListQueryExecutor).
 */
@Component
public class GroupListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    /** Roles of the group row, through its own association (soft-delete filtered). */
    static FilterFieldSet.SubqueryExpression countRoles() {
        return UserDirectoryQueryExecutor.countMembers(Group_.ROLES);
    }

    /** Member count from the {@code User} side — the join table is owned by {@code User.groups}. */
    static FilterFieldSet.SubqueryExpression countMembers() {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<User> user = sq.from(User.class);
            return sq.select(cb.count(user)).where(cb.equal(
                    user.join(User_.GROUPS).get(BaseEntity_.ID), root.get(BaseEntity_.ID)));
        };
    }

    private record GroupRow(UUID id, String name, String description, boolean active,
                            long roleCount, long memberCount) {
    }

    /** Flat projection + batched role/member summaries, assembled into the wire DTO. */
    public Page<GroupResponse> search(@Nullable Specification<Group> spec, Pageable pageable) {
        Page<GroupRow> rows = ProjectionListQuery.execute(entityManager, Group.class, GroupRow.class,
                GroupService.FILTER_FIELDS,
                (root, query, cb) -> cb.construct(GroupRow.class,
                        root.get(BaseEntity_.ID),
                        root.get(Group_.NAME),
                        root.get(Group_.DESCRIPTION),
                        root.get(Group_.ACTIVE),
                        countRoles().apply(root, query, cb),
                        countMembers().apply(root, query, cb)),
                spec, pageable);

        List<GroupResponse> content = assemble(rows.getContent());
        return new org.springframework.data.domain.PageImpl<>(content, pageable, rows.getTotalElements());
    }

    /* ── batched summary resolution (one query per kind per page) ── */

    private List<GroupResponse> assemble(List<GroupRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = new HashSet<>();
        rows.forEach(row -> ids.add(row.id()));
        Map<UUID, List<RoleSummary>> roles = resolveRoles(ids);
        Map<UUID, List<UserSummary>> members = resolveMembers(ids);
        List<GroupResponse> content = new ArrayList<>(rows.size());
        for (GroupRow row : rows) {
            content.add(new GroupResponse(row.id(), row.name(), row.description(), row.active(),
                    roles.getOrDefault(row.id(), List.of()),
                    members.getOrDefault(row.id(), List.of()),
                    row.memberCount()));
        }
        return content;
    }

    private Map<UUID, List<RoleSummary>> resolveRoles(Set<UUID> groupIds) {
        List<Object[]> tuples = entityManager.createQuery("""
                select g.id, r.id, r.name from Group g join g.roles r where g.id in :ids
                """, Object[].class)
                .setParameter("ids", groupIds)
                .getResultList();
        Map<UUID, List<RoleSummary>> byGroup = new HashMap<>();
        for (Object[] tuple : tuples) {
            byGroup.computeIfAbsent((UUID) tuple[0], k -> new ArrayList<>())
                    .add(new RoleSummary((UUID) tuple[1], (String) tuple[2]));
        }
        byGroup.values().forEach(list -> list.sort(Comparator.comparing(RoleSummary::name)));
        return byGroup;
    }

    private Map<UUID, List<UserSummary>> resolveMembers(Set<UUID> groupIds) {
        // t_user_groups is owned by User.groups — the join runs from the member side.
        List<Object[]> tuples = entityManager.createQuery("""
                select g.id, u.id, u.email from User u join u.groups g where g.id in :ids
                """, Object[].class)
                .setParameter("ids", groupIds)
                .getResultList();
        Map<UUID, List<UserSummary>> byGroup = new HashMap<>();
        for (Object[] tuple : tuples) {
            byGroup.computeIfAbsent((UUID) tuple[0], k -> new ArrayList<>())
                    .add(new UserSummary((UUID) tuple[1], (String) tuple[2]));
        }
        byGroup.values().forEach(list -> list.sort(Comparator.comparing(UserSummary::email)));
        return byGroup;
    }
}
