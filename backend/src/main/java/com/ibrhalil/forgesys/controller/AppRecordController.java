package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AppRecordRequest;
import com.ibrhalil.forgesys.dto.AppRecordResponse;
import com.ibrhalil.forgesys.dto.AppRecordSearchRequest;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.service.AppRecordService;
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
@RequestMapping("/api/v1/apps/{appId}/records")
@RequiredArgsConstructor
public class AppRecordController {

    private final AppRecordService appRecordService;

    @GetMapping
    @PreAuthorize("hasAuthority('apps:record:read')")
    public ResponseEntity<PageResponse<AppRecordResponse>> list(
            @PathVariable UUID appId,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        SortGuard.require(pageable, AppRecordService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(appRecordService.list(appId, pageable)));
    }

    @GetMapping("/{recordId}")
    @PreAuthorize("hasAuthority('apps:record:read')")
    public ResponseEntity<AppRecordResponse> get(@PathVariable UUID appId, @PathVariable UUID recordId) {
        return ResponseEntity.ok(appRecordService.findById(appId, recordId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('apps:record:write')")
    public ResponseEntity<AppRecordResponse> create(@PathVariable UUID appId,
                                                    @Valid @RequestBody AppRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appRecordService.create(appId, request));
    }

    @PatchMapping("/{recordId}")
    @PreAuthorize("hasAuthority('apps:record:write')")
    public ResponseEntity<AppRecordResponse> update(@PathVariable UUID appId,
                                                    @PathVariable UUID recordId,
                                                    @Valid @RequestBody AppRecordRequest request) {
        return ResponseEntity.ok(appRecordService.update(appId, recordId, request));
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('apps:record:read')")
    public ResponseEntity<PageResponse<AppRecordResponse>> search(@PathVariable UUID appId,
                                                                  @Valid @RequestBody AppRecordSearchRequest request) {
        return ResponseEntity.ok(PageResponse.of(appRecordService.search(appId, request)));
    }

    @DeleteMapping("/{recordId}")
    @PreAuthorize("hasAuthority('apps:record:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID appId, @PathVariable UUID recordId) {
        appRecordService.delete(appId, recordId);
        return ResponseEntity.noContent().build();
    }
}
