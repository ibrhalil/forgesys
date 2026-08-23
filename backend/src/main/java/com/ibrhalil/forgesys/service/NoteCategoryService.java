package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.dto.NoteCategoryRequest;
import com.ibrhalil.forgesys.dto.NoteCategoryResponse;
import com.ibrhalil.forgesys.entity.NoteCategory;
import com.ibrhalil.forgesys.entity.NoteCategory_;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.NoteCategoryRepository;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Note-category CRUD (K-44 / Epic 3.2) — the shared taxonomy behind
 * {@link NoteService}. Categories are design-bounded data (a handful per tenant),
 * so the list is a plain paged read with a {@code q} name search (no filter engine
 * surface). Deleting a category leaves its notes in place — the FK is
 * {@code ON DELETE SET NULL}, notes become uncategorized.
 */
@Service
@RequiredArgsConstructor
public class NoteCategoryService {

    /** Sortable direct attributes; {@code q} matches {@code name}. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(NoteCategory_.NAME, FilterFieldType.STRING, true)
            .build();

    private final NoteCategoryRepository noteCategoryRepository;

    @Transactional(readOnly = true)
    public Page<NoteCategoryResponse> search(String q, Pageable pageable) {
        Specification<NoteCategory> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, List.of());
        return noteCategoryRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NoteCategoryResponse findById(UUID id) {
        return toResponse(getCategoryOrThrow(id));
    }

    @Transactional
    @AuditLog(action = "note_category_created", entityType = "NoteCategory", entityId = "#result.id", entityName = "#result.name")
    public NoteCategoryResponse create(NoteCategoryRequest request) {
        if (noteCategoryRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.NOTE_CATEGORY_NAME_TAKEN,
                    "Note category name already exists: " + request.name());
        }
        NoteCategory category = new NoteCategory();
        category.setName(request.name());
        category.setColor(request.color());
        NoteCategory saved = noteCategoryRepository.save(category);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "note_category_updated", entityType = "NoteCategory", entityId = "#result.id", entityName = "#result.name")
    public NoteCategoryResponse update(UUID id, NoteCategoryRequest request) {
        NoteCategory category = getCategoryOrThrow(id);
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
        // Soft-delete the category; the FK's ON DELETE SET NULL never fires (soft-delete
        // is an UPDATE), but Hibernate's @SQLRestriction already hides it from reads.
        // Notes keep their categoryId column value — resolveCategoryName treats a
        // soft-deleted category as absent (name chip simply disappears).
        noteCategoryRepository.deleteById(id);
    }

    private NoteCategory getCategoryOrThrow(UUID id) {
        return noteCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Note category not found: " + id));
    }

    private NoteCategoryResponse toResponse(NoteCategory category) {
        return new NoteCategoryResponse(category.getId(), category.getName(), category.getColor());
    }
}
