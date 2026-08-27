package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.PermissionResponse;
import com.ibrhalil.forgesys.dto.RoleResponse;
import com.ibrhalil.forgesys.dto.RoleSummary;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.Role_;
import com.ibrhalil.forgesys.web.projection.ProjectionListQuery;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
 * Read side of the role list (K-49): one flat Criteria DTO projection + two batch
 * queries resolving the page's explicit-permission and parent summaries.
 * {@code permissions}/{@code parents} stay batched lists (they carry full summaries);
 * the count subquery keeps {@code permissionCount} filterable/sortable in the DB.
 * Soft-deleted excluded by the entity-path soft-delete filter.
 */
@Component
public class RoleListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    private record RoleRow(UUID id, String name, String description, boolean allPermissions, long permissionCount) {
    }

    public Page<RoleResponse> search(@Nullable Specification<Role> spec, Pageable pageable) {
        Page<RoleRow> rows = ProjectionListQuery.execute(entityManager, Role.class, RoleRow.class,
                RoleService.FILTER_FIELDS,
                (root, query, cb) -> cb.construct(RoleRow.class,
                        root.get(BaseEntity_.ID),
                        root.get(Role_.NAME),
                        root.get(Role_.DESCRIPTION),
                        root.get(Role_.ALL_PERMISSIONS),
                        UserDirectoryQueryExecutor.countMembers(Role_.PERMISSIONS).apply(root, query, cb)),
                spec, pageable);

        List<RoleResponse> content = assemble(rows.getContent());
        return new PageImpl<>(content, pageable, rows.getTotalElements());
    }

    private List<RoleResponse> assemble(List<RoleRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = new HashSet<>();
        rows.forEach(row -> ids.add(row.id()));
        Map<UUID, List<PermissionResponse>> permissions = resolvePermissions(ids);
        Map<UUID, List<RoleSummary>> parents = resolveParents(ids);
        List<RoleResponse> content = new ArrayList<>(rows.size());
        for (RoleRow row : rows) {
            content.add(new RoleResponse(row.id(), row.name(), row.description(), row.allPermissions(),
                    permissions.getOrDefault(row.id(), List.of()),
                    parents.getOrDefault(row.id(), List.of())));
        }
        return content;
    }

    private Map<UUID, List<PermissionResponse>> resolvePermissions(Set<UUID> roleIds) {
        List<Object[]> tuples = entityManager.createQuery("""
                select r.id, p.id, p.name, p.description from Role r join r.permissions p where r.id in :ids
                """, Object[].class)
                .setParameter("ids", roleIds)
                .getResultList();
        Map<UUID, List<PermissionResponse>> byRole = new HashMap<>();
        for (Object[] tuple : tuples) {
            byRole.computeIfAbsent((UUID) tuple[0], k -> new ArrayList<>())
                    .add(new PermissionResponse((UUID) tuple[1], (String) tuple[2], (String) tuple[3]));
        }
        byRole.values().forEach(list -> list.sort(Comparator.comparing(PermissionResponse::name)));
        return byRole;
    }

    private Map<UUID, List<RoleSummary>> resolveParents(Set<UUID> roleIds) {
        List<Object[]> tuples = entityManager.createQuery("""
                select r.id, p.id, p.name from Role r join r.parentRoles p where r.id in :ids
                """, Object[].class)
                .setParameter("ids", roleIds)
                .getResultList();
        Map<UUID, List<RoleSummary>> byRole = new HashMap<>();
        for (Object[] tuple : tuples) {
            byRole.computeIfAbsent((UUID) tuple[0], k -> new ArrayList<>())
                    .add(new RoleSummary((UUID) tuple[1], (String) tuple[2]));
        }
        byRole.values().forEach(list -> list.sort(Comparator.comparing(RoleSummary::name)));
        return byRole;
    }
}
