package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AppViewRequest;
import com.ibrhalil.forgesys.dto.AppViewResponse;
import com.ibrhalil.forgesys.service.AppBuilderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

/**
 * View endpoints nested under their owning app (K-15 / Epic 3.0.B). Config is a
 * structured DSL validated against the app's property set (see
 * {@code AppViewConfigValidator}). Covered by {@code apps:app:write} — views are part
 * of the app definition, not record data.
 */
@RestController
@RequestMapping("/api/v1/apps/{appId}/views")
@RequiredArgsConstructor
public class AppViewController {

    private final AppBuilderService appBuilderService;

    @GetMapping
    @PreAuthorize("hasAuthority('apps:app:read')")
    public ResponseEntity<List<AppViewResponse>> list(@PathVariable UUID appId) {
        return ResponseEntity.ok(appBuilderService.findById(appId).views());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('apps:app:write')")
    public ResponseEntity<AppViewResponse> create(@PathVariable UUID appId,
                                                  @Valid @RequestBody AppViewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appBuilderService.addView(appId, request));
    }

    @PutMapping("/{viewId}")
    @PreAuthorize("hasAuthority('apps:app:write')")
    public ResponseEntity<AppViewResponse> update(@PathVariable UUID appId,
                                                  @PathVariable UUID viewId,
                                                  @Valid @RequestBody AppViewRequest request) {
        return ResponseEntity.ok(appBuilderService.updateView(appId, viewId, request));
    }

    @DeleteMapping("/{viewId}")
    @PreAuthorize("hasAuthority('apps:app:write')")
    public ResponseEntity<Void> delete(@PathVariable UUID appId, @PathVariable UUID viewId) {
        appBuilderService.deleteView(appId, viewId);
        return ResponseEntity.noContent().build();
    }
}
