package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.TaskRequest;
import com.ibrhalil.forgesys.dto.TaskResponse;
import com.ibrhalil.forgesys.service.TaskService;
import com.ibrhalil.forgesys.web.SortGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Task endpoints nested under their owning project. A task is only addressable through
 * its project; a task of another project yields 404 (scoped lookup, no cross-project leak).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    @PreAuthorize("hasAuthority('pm:task:read')")
    public ResponseEntity<PageResponse<TaskResponse>> list(
            @PathVariable UUID projectId,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        SortGuard.require(pageable, TaskService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(taskService.list(projectId, pageable)));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("hasAuthority('pm:task:read')")
    public ResponseEntity<TaskResponse> get(@PathVariable UUID projectId, @PathVariable UUID taskId) {
        return ResponseEntity.ok(taskService.findById(projectId, taskId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pm:task:write')")
    public ResponseEntity<TaskResponse> create(@PathVariable UUID projectId,
                                               @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(projectId, request));
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("hasAuthority('pm:task:write')")
    public ResponseEntity<TaskResponse> update(@PathVariable UUID projectId,
                                               @PathVariable UUID taskId,
                                               @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(taskService.update(projectId, taskId, request));
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasAuthority('pm:task:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID taskId) {
        taskService.delete(projectId, taskId);
        return ResponseEntity.noContent().build();
    }
}
