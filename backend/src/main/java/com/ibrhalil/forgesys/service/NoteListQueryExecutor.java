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
 * Read side of the note list (K-49): a single Criteria DTO projection that carries
 * the category/project names as correlated scalar subqueries over the plain FK
 * columns (the K-45 convention — notes hold {@code categoryId}/{@code projectId} as
 * UUIDs, not associations), replacing the per-page batch name-resolution queries.
 * The resolved names are first-class filter/sort targets ({@code projectName},
 * {@code categoryName}) and stay in sync with the row by construction.
 *
 * <p>The referenced entity's {@code @SQLRestriction} applies inside the subqueries —
 * a soft-deleted category/project resolves to {@code null} rather than a stale name.
 */
@Component
public class NoteListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Correlated scalar subquery resolving a referenced entity's name through a plain
     * FK column (no association): {@code (select t.<nameAttr> from Target t where
     * t.<idAttr> = root.<linkAttr>)}.
     */
    static FilterFieldSet.SubqueryExpression referencedName(Class<?> targetEntity, String linkIdAttribute,
            String targetIdAttribute, String targetNameAttribute) {
        return (root, query, cb) -> {
            Subquery<String> sq = query.subquery(String.class);
            Root<?> target = sq.from(targetEntity);
            return sq.select(target.get(targetNameAttribute))
                    .where(cb.equal(target.get(targetIdAttribute), root.get(linkIdAttribute)));
        };
    }

    static FilterFieldSet.SubqueryExpression projectName() {
        return referencedName(Project.class, Note_.PROJECT_ID, BaseEntity_.ID, Project_.NAME);
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
                        projectName().apply(root, query, cb),
                        root.get(Note_.CATEGORY_ID),
                        categoryName().apply(root, query, cb),
                        root.get(Note_.PINNED),
                        root.get(AuditEntity_.UPDATED_AT)),
                spec, pageable);
    }
}
