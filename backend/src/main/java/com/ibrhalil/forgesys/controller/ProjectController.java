package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.ProjectRequest;
import com.ibrhalil.forgesys.dto.ProjectResponse;
import com.ibrhalil.forgesys.dto.ProjectTypeResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.service.ProjectService;
import com.ibrhalil.forgesys.web.SortGuard;
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

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @PreAuthorize("hasAuthority('pm:project:read')")
    public ResponseEntity<PageResponse<ProjectResponse>> list(
            @PageableDefault(sort = "name") Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) UUID parentProjectId,
            @RequestParam(required = false) ProjectType type) {
        SortGuard.require(pageable, ProjectService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(
                projectService.search(q, qFields, parentProjectId, type, pageable)));
    }

    /** Filter-engine variant of the list: paging + multi-sort + filters + {@code q} in one POST body. */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('pm:project:read')")
    public ResponseEntity<PageResponse<ProjectResponse>> search(@Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, ProjectService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(projectService.search(request, pageable)));
    }

    /** Creatable type catalog from ACTIVE modules (K-45); registry-bounded list — never DB-paged. */
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('pm:project:read')")
    public ResponseEntity<List<ProjectTypeResponse>> types() {
        return ResponseEntity.ok(projectService.listAvailableTypes());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pm:project:read')")
    public ResponseEntity<ProjectResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pm:project:write')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pm:project:write')")
    public ResponseEntity<ProjectResponse> update(@PathVariable UUID id, @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('pm:project:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
