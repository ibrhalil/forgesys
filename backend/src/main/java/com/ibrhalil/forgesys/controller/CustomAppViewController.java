package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.CustomAppViewRequest;
import com.ibrhalil.forgesys.dto.CustomAppViewResponse;
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
 * Views nested under their owning app (K-15); a view of another app yields 404.
 * Config is a structured DSL re-validated against the app's property set; covered
 * by {@code apps:customapp:write}.
 */
@RestController
@RequestMapping("/api/v1/custom-apps/{customAppId}/views")
@RequiredArgsConstructor
public class CustomAppViewController {

    private final CustomAppService customAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('apps:customapp:read')")
    public ResponseEntity<List<CustomAppViewResponse>> list(@PathVariable UUID customAppId) {
        return ResponseEntity.ok(customAppService.findById(customAppId).views());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('apps:customapp:write')")
    public ResponseEntity<CustomAppViewResponse> create(@PathVariable UUID customAppId,
                                                  @Valid @RequestBody CustomAppViewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customAppService.addView(customAppId, request));
    }

    @PutMapping("/{viewId}")
    @PreAuthorize("hasAuthority('apps:customapp:write')")
    public ResponseEntity<CustomAppViewResponse> update(@PathVariable UUID customAppId,
                                                  @PathVariable UUID viewId,
                                                  @Valid @RequestBody CustomAppViewRequest request) {
        return ResponseEntity.ok(customAppService.updateView(customAppId, viewId, request));
    }

    @DeleteMapping("/{viewId}")
    @PreAuthorize("hasAuthority('apps:customapp:write')")
    public ResponseEntity<Void> delete(@PathVariable UUID customAppId, @PathVariable UUID viewId) {
        customAppService.deleteView(customAppId, viewId);
        return ResponseEntity.noContent().build();
    }
}
