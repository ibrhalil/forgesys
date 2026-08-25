package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.PermissionResponse;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Permission_;
import com.ibrhalil.forgesys.web.projection.ProjectionListQuery;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read side of the permission list (K-49): a flat, join-less Criteria DTO projection
 * over {@code t_permissions} — DTO rows instead of managed entities, running through
 * the shared filter engine like every other list.
 */
@Component
public class PermissionListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    public Page<PermissionResponse> search(@Nullable Specification<Permission> spec, Pageable pageable) {
        return ProjectionListQuery.execute(entityManager, Permission.class, PermissionResponse.class,
                PermissionService.FILTER_FIELDS,
                (root, query, cb) -> cb.construct(PermissionResponse.class,
                        root.get(BaseEntity_.ID),
                        root.get(Permission_.NAME),
                        root.get(Permission_.DESCRIPTION)),
                spec, pageable);
    }
}
