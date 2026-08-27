package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.CustomAppResponse;
import com.ibrhalil.forgesys.entity.CustomApp;
import com.ibrhalil.forgesys.entity.CustomApp_;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.web.projection.ProjectionListQuery;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** CustomApp list read side (K-49); {@code projectName} via the K-45 plain-FK subquery convention. */
@Component
public class CustomAppListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    public Page<CustomAppResponse> search(@Nullable Specification<CustomApp> spec, Pageable pageable) {
        return ProjectionListQuery.execute(entityManager, CustomApp.class, CustomAppResponse.class,
                CustomAppService.FILTER_FIELDS,
                (root, query, cb) -> cb.construct(CustomAppResponse.class,
                        root.get(BaseEntity_.ID),
                        root.get(CustomApp_.NAME),
                        root.get(CustomApp_.DESCRIPTION),
                        root.get(CustomApp_.ICON),
                        root.get(CustomApp_.PROJECT_ID),
                        NoteListQueryExecutor.projectNameOf(CustomApp_.PROJECT_ID).apply(root, query, cb),
                        root.get(AuditEntity_.CREATED_DATE),
                        root.get(AuditEntity_.UPDATED_AT)),
                spec, pageable);
    }
}
