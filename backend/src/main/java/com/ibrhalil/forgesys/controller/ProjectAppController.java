package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AppRequest;
import com.ibrhalil.forgesys.dto.AppResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.service.AppBuilderService;
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
 * Custom apps nested under their APPS-type collection container (K-45 step 5) — the
 * TaskController pattern: the container must exist (404) and be an APPS container
 * (409 {@code project_type_mismatch}). Tenant-level plan limits still apply on the
 * nested create. Update/delete stay on the flat {@code /apps/{id}} surface.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/apps")
@RequiredArgsConstructor
public class ProjectAppController {

    private final AppBuilderService appBuilderService;

    @GetMapping
    @PreAuthorize("hasAuthority('apps:app:read')")
    public ResponseEntity<PageResponse<AppResponse>> list(
            @PathVariable UUID projectId,
            @PageableDefault(sort = "name") Pageable pageable,
            @RequestParam(required = false) String q) {
        SortGuard.require(pageable, AppBuilderService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(appBuilderService.searchInProject(projectId, q, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('apps:app:write')")
    public ResponseEntity<AppResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody AppRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appBuilderService.createInProject(projectId, request));
    }
}
