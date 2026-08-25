package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.ProjectResponse;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.Project_;
import com.ibrhalil.forgesys.web.projection.ProjectionListQuery;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read side of the project list (K-49): flat Criteria DTO projection. The
 * {@code parentProjectName} column (display + filter + sort) resolves as a correlated
 * scalar subquery over the plain self-FK column — a soft-deleted parent yields null.
 */
@Component
public class ProjectListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    public Page<ProjectResponse> search(@Nullable Specification<Project> spec, Pageable pageable) {
        return ProjectionListQuery.execute(entityManager, Project.class, ProjectResponse.class,
                ProjectService.FILTER_FIELDS,
                (root, query, cb) -> cb.construct(ProjectResponse.class,
                        root.get(BaseEntity_.ID),
                        root.get(Project_.NAME),
                        root.get(Project_.DESCRIPTION),
                        root.get(Project_.TYPE),
                        root.get(Project_.PARENT_PROJECT_ID),
                        root.get(Project_.IS_DEFAULT)),
                spec, pageable);
    }
}
