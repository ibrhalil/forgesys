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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Note categories nested under their NOTES-type project container (K-45) — the
 * TaskController pattern: 404 unknown container, 409 {@code project_type_mismatch}
 * for a non-NOTES one. Update/delete stay on the flat {@code /note-categories/{id}}
 * surface; a category's project is fixed at create time.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/note-categories")
@RequiredArgsConstructor
public class ProjectNoteCategoryController {

    private final NoteCategoryService noteCategoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('notes:category:read')")
    public ResponseEntity<PageResponse<NoteCategoryResponse>> list(
            @PathVariable UUID projectId,
            @PageableDefault(sort = "name") Pageable pageable,
            @RequestParam(required = false) String q) {
        SortGuard.require(pageable, NoteCategoryService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(noteCategoryService.searchInProject(projectId, q, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('notes:category:write')")
    public ResponseEntity<NoteCategoryResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody NoteCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(noteCategoryService.createInProject(projectId, request));
    }
}
