package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.dto.NoteRequest;
import com.ibrhalil.forgesys.dto.NoteResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.Note;
import com.ibrhalil.forgesys.entity.NoteCategory;
import com.ibrhalil.forgesys.entity.Note_;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.NoteCategoryRepository;
import com.ibrhalil.forgesys.persistence.repository.NoteRepository;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
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
 * Note CRUD anchored to NOTES-type containers (K-44/K-45): the flat list is the
 * cross-container view ({@code ?projectId=} narrows); flat creates default to "Genel".
 * Visibility stays tenant-wide (ABAC deferred); names resolved batched per page.
 * Rationale: docs/CODE_NOTES.md (backend/service → NoteService).
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    /**
     * Filterable/sortable attributes of the note list (K-49); {@code q} matches title,
     * content, project and category names. {@code categoryName}/{@code projectName} are
     * correlated subqueries over the plain FK columns (soft-deleted refs → null).
     */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Note_.TITLE, FilterFieldType.STRING, true)
            .field(Note_.CONTENT, FilterFieldType.STRING, true)
            .field(Note_.PINNED, FilterFieldType.BOOLEAN, false)
            .field(Note_.CATEGORY_ID, FilterFieldType.UUID, false)
            .field(Note_.PROJECT_ID, FilterFieldType.UUID, false)
            .subqueryField("categoryName", FilterFieldType.STRING, true, NoteListQueryExecutor.categoryName())
            .subqueryField("projectName", FilterFieldType.STRING, true,
                    NoteListQueryExecutor.projectNameOf(Note_.PROJECT_ID))
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final NoteRepository noteRepository;
    private final NoteCategoryRepository noteCategoryRepository;
    private final ProjectRepository projectRepository;
    private final ProjectContainerSupport projectContainerSupport;
    private final NoteListQueryExecutor noteListQueryExecutor;

    @Transactional(readOnly = true)
    public Page<NoteResponse> search(String q, List<String> qFields, UUID categoryId, Boolean pinned,
            UUID projectId, Pageable pageable) {
        List<FilterCriteria> filters = new ArrayList<>();
        if (categoryId != null) {
            filters.add(new FilterCriteria(Note_.CATEGORY_ID, FilterOperator.EQ, List.of(categoryId.toString())));
        }
        if (pinned != null) {
            filters.add(new FilterCriteria(Note_.PINNED, FilterOperator.EQ, List.of(pinned.toString())));
        }
        if (projectId != null) {
            filters.add(new FilterCriteria(Note_.PROJECT_ID, FilterOperator.EQ, List.of(projectId.toString())));
        }
        return doSearch(q, qFields, filters, pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /notes/search}. */
    @Transactional(readOnly = true)
    public Page<NoteResponse> search(SearchRequest request, Pageable pageable) {
        return doSearch(request.q(), request.qFields(), request.filters(), pageable);
    }

    private Page<NoteResponse> doSearch(String q, List<String> qFields, List<FilterCriteria> filters,
            Pageable pageable) {
        Specification<Note> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, qFields, filters);
        return noteListQueryExecutor.search(spec, pageable);
    }

    /** Cross-container list narrowed to one container (nested endpoint, K-45). */
    @Transactional(readOnly = true)
    public Page<NoteResponse> searchInProject(UUID projectId, String q, List<String> qFields, UUID categoryId,
            Boolean pinned, Pageable pageable) {
        projectContainerSupport.assertProject(ProjectType.NOTES, projectId);
        return search(q, qFields, categoryId, pinned, projectId, pageable);
    }

    @Transactional(readOnly = true)
    public NoteResponse findById(UUID id) {
        Note note = getNoteOrThrow(id);
        return toResponse(note, resolveCategoryName(note.getCategoryId()), resolveProjectName(note.getProjectId()));
    }

    @Transactional
    @AuditLog(action = "note_created", entityType = "Note", entityId = "#result.id", entityName = "#result.title")
    public NoteResponse create(NoteRequest request) {
        Project target = projectContainerSupport.resolveTarget(ProjectType.NOTES, request.projectId());
        return createIn(target, request);
    }

    /** Nested create (K-45): the project must be a NOTES container (404/409 otherwise). */
    @Transactional
    @AuditLog(action = "note_created", entityType = "Note", entityId = "#result.id", entityName = "#result.title")
    public NoteResponse createInProject(UUID projectId, NoteRequest request) {
        Project target = projectContainerSupport.assertProject(ProjectType.NOTES, projectId);
        return createIn(target, request);
    }

    @Transactional
    @AuditLog(action = "note_updated", entityType = "Note", entityId = "#result.id", entityName = "#result.title")
    public NoteResponse update(UUID id, NoteRequest request) {
        Note note = getNoteOrThrow(id);
        UUID targetProjectId = note.getProjectId();
        if (request.projectId() != null && !request.projectId().equals(targetProjectId)) {
            targetProjectId = projectContainerSupport.assertProject(ProjectType.NOTES, request.projectId()).getId();
        }
        validateCategory(request.categoryId(), targetProjectId);
        note.setTitle(request.title());
        note.setContent(request.content() == null ? "" : request.content());
        note.setCategoryId(request.categoryId());
        note.setProjectId(targetProjectId);
        if (request.pinned() != null) {
            note.setPinned(request.pinned());
        }
        Note saved = noteRepository.save(note);
        return toResponse(saved, resolveCategoryName(saved.getCategoryId()), resolveProjectName(saved.getProjectId()));
    }

    @Transactional
    @AuditLog(action = "note_deleted", entityType = "Note", entityId = "#id", entityName = "")
    public void delete(UUID id) {
        if (!noteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Note not found: " + id);
        }
        noteRepository.deleteById(id);
    }

    private NoteResponse createIn(Project target, NoteRequest request) {
        validateCategory(request.categoryId(), target.getId());
        Note note = new Note();
        note.setTitle(request.title());
        note.setContent(request.content() == null ? "" : request.content());
        note.setCategoryId(request.categoryId());
        note.setProjectId(target.getId());
        note.setPinned(request.pinned() != null && request.pinned());
        Note saved = noteRepository.save(note);
        return toResponse(saved, resolveCategoryName(saved.getCategoryId()), target.getName());
    }

    private Note getNoteOrThrow(UUID id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found: " + id));
    }

    /** The category must exist (404) and belong to the same container as the note (409). */
    private void validateCategory(UUID categoryId, UUID projectId) {
        if (categoryId == null) {
            return;
        }
        NoteCategory category = noteCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Note category not found: " + categoryId));
        if (!Objects.equals(category.getProjectId(), projectId)) {
            throw new BusinessException(ErrorCode.NOTE_CATEGORY_PROJECT_MISMATCH,
                    "Note category %s belongs to project %s, not %s"
                            .formatted(categoryId, category.getProjectId(), projectId));
        }
    }

    private String resolveCategoryName(UUID categoryId) {
        return categoryId == null ? null
                : noteCategoryRepository.findById(categoryId).map(NoteCategory::getName).orElse(null);
    }

    private String resolveProjectName(UUID projectId) {
        return projectId == null ? null
                : projectRepository.findById(projectId).map(Project::getName).orElse(null);
    }

    private NoteResponse toResponse(Note note, String categoryName, String projectName) {
        return new NoteResponse(note.getId(), note.getTitle(), note.getContent(),
                note.getProjectId(), projectName, note.getCategoryId(), categoryName,
                note.isPinned(), note.getUpdatedAt());
    }
}
