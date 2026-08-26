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

/** Project list read side (K-49); {@code parentProjectName} via the self-FK subquery (soft-deleted parent → null). */
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
