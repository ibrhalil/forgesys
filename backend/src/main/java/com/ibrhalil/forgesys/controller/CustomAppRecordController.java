package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.CustomAppRecordRequest;
import com.ibrhalil.forgesys.dto.CustomAppRecordResponse;
import com.ibrhalil.forgesys.dto.CustomAppRecordSearchRequest;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.service.CustomAppRecordService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Records (rows) nested under their owning app (K-15): a record of another app
 * yields 404 (scoped lookup). Update is PATCH (partial value merge); /search
 * runs the PostgreSQL JSONB filter path.
 */
@RestController
@RequestMapping("/api/v1/custom-apps/{customAppId}/records")
@RequiredArgsConstructor
public class CustomAppRecordController {

    private final CustomAppRecordService customAppRecordService;

    @GetMapping
    @PreAuthorize("hasAuthority('apps:record:read')")
    public ResponseEntity<PageResponse<CustomAppRecordResponse>> list(
            @PathVariable UUID customAppId,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        SortGuard.require(pageable, CustomAppRecordService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(customAppRecordService.list(customAppId, pageable)));
    }

    @GetMapping("/{recordId}")
    @PreAuthorize("hasAuthority('apps:record:read')")
    public ResponseEntity<CustomAppRecordResponse> get(@PathVariable UUID customAppId, @PathVariable UUID recordId) {
        return ResponseEntity.ok(customAppRecordService.findById(customAppId, recordId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('apps:record:write')")
    public ResponseEntity<CustomAppRecordResponse> create(@PathVariable UUID customAppId,
                                                    @Valid @RequestBody CustomAppRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customAppRecordService.create(customAppId, request));
    }

    @PatchMapping("/{recordId}")
    @PreAuthorize("hasAuthority('apps:record:write')")
    public ResponseEntity<CustomAppRecordResponse> update(@PathVariable UUID customAppId,
                                                    @PathVariable UUID recordId,
                                                    @Valid @RequestBody CustomAppRecordRequest request) {
        return ResponseEntity.ok(customAppRecordService.update(customAppId, recordId, request));
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('apps:record:read')")
    public ResponseEntity<PageResponse<CustomAppRecordResponse>> search(@PathVariable UUID customAppId,
                                                                  @Valid @RequestBody CustomAppRecordSearchRequest request) {
        return ResponseEntity.ok(PageResponse.of(customAppRecordService.search(customAppId, request)));
    }

    @DeleteMapping("/{recordId}")
    @PreAuthorize("hasAuthority('apps:record:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID customAppId, @PathVariable UUID recordId) {
        customAppRecordService.delete(customAppId, recordId);
        return ResponseEntity.noContent().build();
    }
}
