package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.NoteCategoryRequest;
import com.ibrhalil.forgesys.dto.NoteCategoryResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.service.NoteCategoryService;
import com.ibrhalil.forgesys.web.SortGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Note-category CRUD (K-44 / Epic 3.2) — the shared taxonomy behind
 * {@link NoteController}. Paged with a {@code q} name search (categories are
 * design-bounded, so no filter-engine surface beyond the search).
 */
@RestController
@RequestMapping("/api/v1/note-categories")
@RequiredArgsConstructor
public class NoteCategoryController {

    private final NoteCategoryService noteCategoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('notes:category:read')")
    public ResponseEntity<PageResponse<NoteCategoryResponse>> list(
            @PageableDefault(sort = "name") Pageable pageable,
            @RequestParam(required = false) String q) {
        SortGuard.require(pageable, NoteCategoryService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(noteCategoryService.search(q, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('notes:category:read')")
    public ResponseEntity<NoteCategoryResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(noteCategoryService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('notes:category:write')")
    public ResponseEntity<NoteCategoryResponse> create(@Valid @RequestBody NoteCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteCategoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('notes:category:write')")
    public ResponseEntity<NoteCategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody NoteCategoryRequest request) {
        return ResponseEntity.ok(noteCategoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('notes:category:write')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        noteCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
