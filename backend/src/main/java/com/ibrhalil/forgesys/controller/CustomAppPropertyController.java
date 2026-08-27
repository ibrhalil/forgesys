package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.CustomAppPropertyRequest;
import com.ibrhalil.forgesys.dto.CustomAppPropertyResponse;
import com.ibrhalil.forgesys.service.CustomAppService;
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
 * Properties (columns) nested under their owning app (K-15); a property of
 * another app yields 404. Covered by {@code apps:customapp:write} — part of the app
 * definition, not record data.
 */
@RestController
@RequestMapping("/api/v1/custom-apps/{customAppId}/properties")
@RequiredArgsConstructor
public class CustomAppPropertyController {

    private final CustomAppService customAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('apps:customapp:read')")
    public ResponseEntity<List<CustomAppPropertyResponse>> list(@PathVariable UUID customAppId) {
        return ResponseEntity.ok(customAppService.findById(customAppId).properties());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('apps:customapp:write')")
    public ResponseEntity<CustomAppPropertyResponse> create(@PathVariable UUID customAppId,
                                                      @Valid @RequestBody CustomAppPropertyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customAppService.addProperty(customAppId, request));
    }

    @PutMapping("/{propertyId}")
    @PreAuthorize("hasAuthority('apps:customapp:write')")
    public ResponseEntity<CustomAppPropertyResponse> update(@PathVariable UUID customAppId,
                                                      @PathVariable UUID propertyId,
                                                      @Valid @RequestBody CustomAppPropertyRequest request) {
        return ResponseEntity.ok(customAppService.updateProperty(customAppId, propertyId, request));
    }

    @DeleteMapping("/{propertyId}")
    @PreAuthorize("hasAuthority('apps:customapp:write')")
    public ResponseEntity<Void> delete(@PathVariable UUID customAppId, @PathVariable UUID propertyId) {
        customAppService.deleteProperty(customAppId, propertyId);
        return ResponseEntity.noContent().build();
    }
}
