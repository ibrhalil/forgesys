package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.NoteCategoryRequest;
import com.ibrhalil.forgesys.dto.NoteCategoryResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.service.NoteCategoryService;
import com.ibrhalil.forgesys.web.SortGuard;
import com.ibrhalil.forgesys.web.filter.SearchQuery;
import com.ibrhalil.forgesys.web.filter.SearchRequests;
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

import java.util.List;
import java.util.UUID;

/**
 * Flat cross-container note-category surface (K-44/K-45); container-nested
 * reads/writes live on {@link ProjectNoteCategoryController}.
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
            SearchQuery searchQuery,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) UUID projectId) {
        SortGuard.require(pageable, NoteCategoryService.FILTER_FIELDS);
        if (searchQuery.present()) {
            SearchRequest request = searchQuery.request();
            return ResponseEntity.ok(PageResponse.of(noteCategoryService.search(request, pageable)));
        }
        return ResponseEntity.ok(PageResponse.of(
                noteCategoryService.search(q, qFields, projectId, pageable)));
    }

    /** Filter-engine variant of the list: paging + multi-sort + filters + {@code q} in one POST body. */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('notes:category:read')")
    public ResponseEntity<PageResponse<NoteCategoryResponse>> search(@Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, NoteCategoryService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(noteCategoryService.search(request, pageable)));
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
