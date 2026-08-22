package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AppPropertyRequest;
import com.ibrhalil.forgesys.dto.AppPropertyResponse;
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
 * Property (column) endpoints nested under their owning app (K-15 / Epic 3.0.B). A
 * property is only addressable through its app; a property of another app yields 404
 * (scoped lookup, no cross-app leak). Covered by {@code apps:app:write} — properties
 * are part of the app definition, not record data.
 */
@RestController
@RequestMapping("/api/v1/apps/{appId}/properties")
@RequiredArgsConstructor
public class AppPropertyController {

    private final AppBuilderService appBuilderService;

    @GetMapping
    @PreAuthorize("hasAuthority('apps:app:read')")
    public ResponseEntity<List<AppPropertyResponse>> list(@PathVariable UUID appId) {
        return ResponseEntity.ok(appBuilderService.findById(appId).properties());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('apps:app:write')")
    public ResponseEntity<AppPropertyResponse> create(@PathVariable UUID appId,
                                                      @Valid @RequestBody AppPropertyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appBuilderService.addProperty(appId, request));
    }

    @PutMapping("/{propertyId}")
    @PreAuthorize("hasAuthority('apps:app:write')")
    public ResponseEntity<AppPropertyResponse> update(@PathVariable UUID appId,
                                                      @PathVariable UUID propertyId,
                                                      @Valid @RequestBody AppPropertyRequest request) {
        return ResponseEntity.ok(appBuilderService.updateProperty(appId, propertyId, request));
    }

    @DeleteMapping("/{propertyId}")
    @PreAuthorize("hasAuthority('apps:app:write')")
    public ResponseEntity<Void> delete(@PathVariable UUID appId, @PathVariable UUID propertyId) {
        appBuilderService.deleteProperty(appId, propertyId);
        return ResponseEntity.noContent().build();
    }
}
