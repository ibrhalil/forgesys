package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.dto.NoteCategoryRequest;
import com.ibrhalil.forgesys.dto.NoteCategoryResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.entity.NoteCategory;
import com.ibrhalil.forgesys.entity.NoteCategory_;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.NoteCategoryRepository;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterOperator;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Note-category CRUD inside NOTES-type containers (K-44/K-45). A category's project
 * is fixed at create (a move would strand its notes — rejected 409); deletion leaves
 * its notes in place (they become uncategorized). Name uniqueness stays tenant-wide.
 * Rationale: docs/CODE_NOTES.md (backend/service → NoteCategoryService).
 */
@Service
@RequiredArgsConstructor
public class NoteCategoryService {

    /** Filterable/sortable attributes (K-49); {@code q} matches {@code name}. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(NoteCategory_.NAME, FilterFieldType.STRING, true)
            .field(NoteCategory_.COLOR, FilterFieldType.STRING, false)
            .field(NoteCategory_.PROJECT_ID, FilterFieldType.UUID, false)
            .build();

    private final NoteCategoryRepository noteCategoryRepository;
    private final NoteCategoryListQueryExecutor noteCategoryListQueryExecutor;
    private final ProjectContainerSupport projectContainerSupport;

    @Transactional(readOnly = true)
    public Page<NoteCategoryResponse> search(String q, List<String> qFields, UUID projectId, Pageable pageable) {
        List<FilterCriteria> filters = projectId == null ? List.of()
                : List.of(new FilterCriteria(NoteCategory_.PROJECT_ID, FilterOperator.EQ, List.of(projectId.toString())));
        return doSearch(q, qFields, filters, pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /note-categories/search}. */
    @Transactional(readOnly = true)
    public Page<NoteCategoryResponse> search(SearchRequest request, Pageable pageable) {
        return doSearch(request.q(), request.qFields(), request.filters(), pageable);
    }

    private Page<NoteCategoryResponse> doSearch(String q, List<String> qFields, List<FilterCriteria> filters,
            Pageable pageable) {
        Specification<NoteCategory> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, qFields, filters);
        return noteCategoryListQueryExecutor.search(spec, pageable);
    }

    /** Container-scoped list (nested endpoint, K-45). */
    @Transactional(readOnly = true)
    public Page<NoteCategoryResponse> searchInProject(UUID projectId, String q, List<String> qFields,
            Pageable pageable) {
        projectContainerSupport.assertProject(ProjectType.NOTES, projectId);
        return search(q, qFields, projectId, pageable);
    }

    /** Full {@link SearchRequest} variant of the nested list (K-55 {@code sq} path). */
    @Transactional(readOnly = true)
    public Page<NoteCategoryResponse> searchInProject(UUID projectId, SearchRequest request, Pageable pageable) {
        projectContainerSupport.assertProject(ProjectType.NOTES, projectId);
        List<FilterCriteria> filters = new ArrayList<>(request.filters() == null ? List.of() : request.filters());
        filters.add(new FilterCriteria(NoteCategory_.PROJECT_ID, FilterOperator.EQ, List.of(projectId.toString())));
        return doSearch(request.q(), request.qFields(), filters, pageable);
    }

    @Transactional(readOnly = true)
    public NoteCategoryResponse findById(UUID id) {
        return toResponse(getCategoryOrThrow(id));
    }

    @Transactional
    @AuditLog(action = "note_category_created", entityType = "NoteCategory", entityId = "#result.id", entityName = "#result.name")
    public NoteCategoryResponse create(NoteCategoryRequest request) {
        Project target = projectContainerSupport.resolveTarget(ProjectType.NOTES, request.projectId());
        return createIn(target, request);
    }

    /** Nested create (K-45): the project must be a NOTES container (404/409 otherwise). */
    @Transactional
    @AuditLog(action = "note_category_created", entityType = "NoteCategory", entityId = "#result.id", entityName = "#result.name")
    public NoteCategoryResponse createInProject(UUID projectId, NoteCategoryRequest request) {
        Project target = projectContainerSupport.assertProject(ProjectType.NOTES, projectId);
        return createIn(target, request);
    }

    @Transactional
    @AuditLog(action = "note_category_updated", entityType = "NoteCategory", entityId = "#result.id", entityName = "#result.name")
    public NoteCategoryResponse update(UUID id, NoteCategoryRequest request) {
        NoteCategory category = getCategoryOrThrow(id);
        if (request.projectId() != null && !Objects.equals(request.projectId(), category.getProjectId())) {
            throw new BusinessException(ErrorCode.NOTE_CATEGORY_PROJECT_MISMATCH,
                    "A note category's project cannot change: " + id);
        }
        if (!category.getName().equals(request.name())
                && noteCategoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new BusinessException(ErrorCode.NOTE_CATEGORY_NAME_TAKEN,
                    "Note category name already exists: " + request.name());
        }
        category.setName(request.name());
        category.setColor(request.color());
        NoteCategory saved = noteCategoryRepository.save(category);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "note_category_deleted", entityType = "NoteCategory", entityId = "#id", entityName = "")
    public void delete(UUID id) {
        if (!noteCategoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Note category not found: " + id);
        }
        // Soft-delete: notes keep their categoryId value — a soft-deleted category
        // resolves as absent (the name chip simply disappears).
        noteCategoryRepository.deleteById(id);
    }

    private NoteCategoryResponse createIn(Project target, NoteCategoryRequest request) {
        if (noteCategoryRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.NOTE_CATEGORY_NAME_TAKEN,
                    "Note category name already exists: " + request.name());
        }
        NoteCategory category = new NoteCategory();
        category.setName(request.name());
        category.setColor(request.color());
        category.setProjectId(target.getId());
        NoteCategory saved = noteCategoryRepository.save(category);
        return toResponse(saved);
    }

    private NoteCategory getCategoryOrThrow(UUID id) {
        return noteCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Note category not found: " + id));
    }

    private NoteCategoryResponse toResponse(NoteCategory category) {
        return new NoteCategoryResponse(category.getId(), category.getName(), category.getColor(),
                category.getProjectId());
    }
}
