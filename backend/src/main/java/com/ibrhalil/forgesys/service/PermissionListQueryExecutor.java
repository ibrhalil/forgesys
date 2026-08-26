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

/** Permission list read side (K-49): flat, join-less DTO projection. */
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
