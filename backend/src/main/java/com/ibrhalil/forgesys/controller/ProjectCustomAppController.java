package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.CustomAppRequest;
import com.ibrhalil.forgesys.dto.CustomAppResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.service.CustomAppService;
import com.ibrhalil.forgesys.web.SortGuard;
import com.ibrhalil.forgesys.web.filter.SearchQuery;
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

import java.util.List;
import java.util.UUID;

/**
 * Custom apps nested under their APPS-type container (K-45): unknown → 404,
 * non-APPS → 409 {@code project_type_mismatch}; tenant-level plan limits still
 * apply. Update/delete stay on the flat {@code /custom-apps/{id}} surface.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/custom-apps")
@RequiredArgsConstructor
public class ProjectCustomAppController {

    private final CustomAppService customAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('apps:customapp:read')")
    public ResponseEntity<PageResponse<CustomAppResponse>> list(
            @PathVariable UUID projectId,
            @PageableDefault(sort = "name") Pageable pageable,
            SearchQuery searchQuery,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields) {
        SortGuard.require(pageable, CustomAppService.FILTER_FIELDS);
        if (searchQuery.present()) {
            SearchRequest request = searchQuery.request();
            return ResponseEntity.ok(PageResponse.of(customAppService.searchInProject(projectId, request, pageable)));
        }
        return ResponseEntity.ok(PageResponse.of(
                customAppService.searchInProject(projectId, q, qFields, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('apps:customapp:write')")
    public ResponseEntity<CustomAppResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CustomAppRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customAppService.createInProject(projectId, request));
    }
}
