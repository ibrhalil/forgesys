package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.NoteCategoryResponse;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.NoteCategory;
import com.ibrhalil.forgesys.entity.NoteCategory_;
import com.ibrhalil.forgesys.web.projection.ProjectionListQuery;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** Note-category list read side (K-49): flat, join-less projection ({@code color} filterable). */
@Component
public class NoteCategoryListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    public Page<NoteCategoryResponse> search(@Nullable Specification<NoteCategory> spec, Pageable pageable) {
        return ProjectionListQuery.execute(entityManager, NoteCategory.class, NoteCategoryResponse.class,
                NoteCategoryService.FILTER_FIELDS,
                (root, query, cb) -> cb.construct(NoteCategoryResponse.class,
                        root.get(BaseEntity_.ID),
                        root.get(NoteCategory_.NAME),
                        root.get(NoteCategory_.COLOR),
                        root.get(NoteCategory_.PROJECT_ID)),
                spec, pageable);
    }
}
