package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AppResponse;
import com.ibrhalil.forgesys.entity.App;
import com.ibrhalil.forgesys.entity.App_;
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

/**
 * Read side of the app list (K-49): flat Criteria DTO projection; the APPS container
 * name ({@code projectName}) resolves as a correlated scalar subquery over the plain
 * {@code project_id} column (K-45 convention) — replacing the per-page batch
 * name-resolution and becoming a first-class filter/sort/{@code q} target.
 */
@Component
public class AppListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    public Page<AppResponse> search(@Nullable Specification<App> spec, Pageable pageable) {
        return ProjectionListQuery.execute(entityManager, App.class, AppResponse.class,
                AppBuilderService.FILTER_FIELDS,
                (root, query, cb) -> cb.construct(AppResponse.class,
                        root.get(BaseEntity_.ID),
                        root.get(App_.NAME),
                        root.get(App_.DESCRIPTION),
                        root.get(App_.ICON),
                        root.get(App_.PROJECT_ID),
                        NoteListQueryExecutor.projectNameOf(App_.PROJECT_ID).apply(root, query, cb),
                        root.get(AuditEntity_.CREATED_DATE),
                        root.get(AuditEntity_.UPDATED_AT)),
                spec, pageable);
    }
}
