package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AssignMembersRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.GroupRequest;
import com.ibrhalil.forgesys.dto.GroupResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.service.GroupService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    @PreAuthorize("hasAuthority('iam:group:read')")
    public ResponseEntity<PageResponse<GroupResponse>> list(
            @PageableDefault(sort = "name") Pageable pageable,
            @RequestParam(required = false) String q) {
        SortGuard.require(pageable, GroupService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(groupService.search(q, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:group:read')")
    public ResponseEntity<GroupResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(groupService.findById(id));
    }

    @GetMapping("/{id}/effective-permissions")
    @PreAuthorize("hasAuthority('iam:group:read')")
    public ResponseEntity<List<String>> effectivePermissions(@PathVariable UUID id) {
        return ResponseEntity.ok(groupService.effectivePermissions(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('iam:group:write')")
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody GroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:group:write')")
    public ResponseEntity<GroupResponse> update(@PathVariable UUID id, @Valid @RequestBody GroupRequest request) {
        return ResponseEntity.ok(groupService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:group:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        groupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('iam:group:write')")
    public ResponseEntity<GroupResponse> setRoles(@PathVariable UUID id, @Valid @RequestBody AssignRolesRequest request) {
        return ResponseEntity.ok(groupService.setRoles(id, request));
    }

    @PutMapping("/{id}/members")
    @PreAuthorize("hasAuthority('iam:group:write')")
    public ResponseEntity<GroupResponse> setMembers(@PathVariable UUID id, @Valid @RequestBody AssignMembersRequest request) {
        return ResponseEntity.ok(groupService.setMembers(id, request));
    }
}
