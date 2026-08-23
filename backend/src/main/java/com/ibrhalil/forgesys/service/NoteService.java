package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.dto.NoteRequest;
import com.ibrhalil.forgesys.dto.NoteResponse;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.Note;
import com.ibrhalil.forgesys.entity.Note_;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.NoteCategoryRepository;
import com.ibrhalil.forgesys.persistence.repository.NoteRepository;
import com.ibrhalil.forgesys.dto.FilterCriteria;
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
import java.util.UUID;

/**
 * Tenant-shared note CRUD (K-44 / Epic 3.2). Visibility is tenant-wide by decision
 * (personal/ABAC notes deferred); the category reference is validated on write and
 * resolved to its name on read (single join-free lookup — category names are stable
 * enough for a display chip; the UI re-fetches the category list anyway).
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    /** Filterable/sortable direct attributes of the note list; {@code q} matches title + content. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Note_.TITLE, FilterFieldType.STRING, true)
            .field(Note_.CONTENT, FilterFieldType.STRING, true)
            .field(Note_.PINNED, FilterFieldType.BOOLEAN, false)
            .field(Note_.CATEGORY_ID, FilterFieldType.UUID, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final NoteRepository noteRepository;
    private final NoteCategoryRepository noteCategoryRepository;

    @Transactional(readOnly = true)
    public Page<NoteResponse> search(String q, UUID categoryId, Boolean pinned, Pageable pageable) {
        List<FilterCriteria> filters = new ArrayList<>();
        if (categoryId != null) {
            filters.add(new FilterCriteria(Note_.CATEGORY_ID, FilterOperator.EQ, List.of(categoryId.toString())));
        }
        if (pinned != null) {
            filters.add(new FilterCriteria(Note_.PINNED, FilterOperator.EQ, List.of(pinned.toString())));
        }
        Specification<Note> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, filters);
        return noteRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NoteResponse findById(UUID id) {
        return toResponse(getNoteOrThrow(id));
    }

    @Transactional
    @AuditLog(action = "note_created", entityType = "Note", entityId = "#result.id", entityName = "#result.title")
    public NoteResponse create(NoteRequest request) {
        validateCategory(request.categoryId());
        Note note = new Note();
        note.setTitle(request.title());
        note.setContent(request.content() == null ? "" : request.content());
        note.setCategoryId(request.categoryId());
        note.setPinned(request.pinned() != null && request.pinned());
        Note saved = noteRepository.save(note);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "note_updated", entityType = "Note", entityId = "#result.id", entityName = "#result.title")
    public NoteResponse update(UUID id, NoteRequest request) {
        Note note = getNoteOrThrow(id);
        validateCategory(request.categoryId());
        note.setTitle(request.title());
        note.setContent(request.content() == null ? "" : request.content());
        note.setCategoryId(request.categoryId());
        if (request.pinned() != null) {
            note.setPinned(request.pinned());
        }
        Note saved = noteRepository.save(note);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "note_deleted", entityType = "Note", entityId = "#id", entityName = "")
    public void delete(UUID id) {
        if (!noteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Note not found: " + id);
        }
        noteRepository.deleteById(id);
    }

    private Note getNoteOrThrow(UUID id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found: " + id));
    }

    private void validateCategory(UUID categoryId) {
        if (categoryId != null && !noteCategoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Note category not found: " + categoryId);
        }
    }

    private NoteResponse toResponse(Note note) {
        String categoryName = note.getCategoryId() == null ? null
                : noteCategoryRepository.findById(note.getCategoryId())
                        .map(c -> c.getName())
                        .orElse(null);
        return new NoteResponse(note.getId(), note.getTitle(), note.getContent(),
                note.getCategoryId(), categoryName, note.isPinned(), note.getUpdatedAt());
    }
}
