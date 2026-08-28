package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AssignPermissionsRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.RoleRequest;
import com.ibrhalil.forgesys.dto.RoleResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.service.RoleService;
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

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('iam:role:read')")
    public ResponseEntity<PageResponse<RoleResponse>> list(
            @PageableDefault(sort = "name") Pageable pageable,
            SearchQuery searchQuery,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields) {
        SortGuard.require(pageable, RoleService.FILTER_FIELDS);
        if (searchQuery.present()) {
            SearchRequest request = searchQuery.request();
            return ResponseEntity.ok(PageResponse.of(roleService.search(request, pageable)));
        }
        return ResponseEntity.ok(PageResponse.of(roleService.search(q, qFields, pageable)));
    }

    /** Filter-engine variant of the list: paging + multi-sort + filters + {@code q} in one POST body. */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('iam:role:read')")
    public ResponseEntity<PageResponse<RoleResponse>> search(@Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, RoleService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(roleService.search(request, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:role:read')")
    public ResponseEntity<RoleResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('iam:role:write')")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:role:write')")
    public ResponseEntity<RoleResponse> update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:role:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('iam:role:write')")
    public ResponseEntity<RoleResponse> setPermissions(@PathVariable UUID id,
                                                       @Valid @RequestBody AssignPermissionsRequest request) {
        return ResponseEntity.ok(roleService.setPermissions(id, request));
    }

    /** Replaces the roles this role inherits permissions from (role inheritance). */
    @PutMapping("/{id}/parents")
    @PreAuthorize("hasAuthority('iam:role:write')")
    public ResponseEntity<RoleResponse> setParents(@PathVariable UUID id,
                                                   @Valid @RequestBody AssignRolesRequest request) {
        return ResponseEntity.ok(roleService.setParents(id, request));
    }
}
