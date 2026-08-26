package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.NoteResponse;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.Note;
import com.ibrhalil.forgesys.entity.NoteCategory;
import com.ibrhalil.forgesys.entity.NoteCategory_;
import com.ibrhalil.forgesys.entity.Note_;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.Project_;
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

/**
 * Read side of the note list (K-49): category/project names carried as correlated
 * scalar subqueries over the plain FK columns (K-45 — notes hold UUIDs, not
 * associations), making them first-class filter/sort targets that stay in sync with
 * the row by construction. {@code @SQLRestriction} applies inside the subqueries —
 * a soft-deleted reference resolves to null.
 */
@Component
public class NoteListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    /** Correlated scalar subquery resolving a referenced entity's name via a plain FK column. */
    static FilterFieldSet.SubqueryExpression referencedName(Class<?> targetEntity, String linkIdAttribute,
            String targetIdAttribute, String targetNameAttribute) {
        return (root, query, cb) -> {
            Subquery<String> sq = query.subquery(String.class);
            Root<?> target = sq.from(targetEntity);
            return sq.select(target.get(targetNameAttribute))
                    .where(cb.equal(target.get(targetIdAttribute), root.get(linkIdAttribute)));
        };
    }

    /** Project-name subquery anchored at an arbitrary project-id column. */
    static FilterFieldSet.SubqueryExpression projectNameOf(String linkIdAttribute) {
        return referencedName(Project.class, linkIdAttribute, BaseEntity_.ID, Project_.NAME);
    }

    static FilterFieldSet.SubqueryExpression categoryName() {
        return referencedName(NoteCategory.class, Note_.CATEGORY_ID, BaseEntity_.ID, NoteCategory_.NAME);
    }

    public Page<NoteResponse> search(@Nullable Specification<Note> spec, Pageable pageable) {
        return ProjectionListQuery.execute(entityManager, Note.class, NoteResponse.class,
                NoteService.FILTER_FIELDS,
                (root, query, cb) -> cb.construct(NoteResponse.class,
                        root.get(BaseEntity_.ID),
                        root.get(Note_.TITLE),
                        root.get(Note_.CONTENT),
                        root.get(Note_.PROJECT_ID),
                        projectNameOf(Note_.PROJECT_ID).apply(root, query, cb),
                        root.get(Note_.CATEGORY_ID),
                        categoryName().apply(root, query, cb),
                        root.get(Note_.PINNED),
                        root.get(AuditEntity_.UPDATED_AT)),
                spec, pageable);
    }
}
