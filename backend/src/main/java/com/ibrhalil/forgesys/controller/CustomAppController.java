package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.CustomAppDetailResponse;
import com.ibrhalil.forgesys.dto.CustomAppPlanLimitsResponse;
import com.ibrhalil.forgesys.dto.CustomAppRequest;
import com.ibrhalil.forgesys.dto.CustomAppResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.service.CustomAppService;
import com.ibrhalil.forgesys.service.PlanLimitService;
import com.ibrhalil.forgesys.web.SortGuard;
import com.ibrhalil.forgesys.web.filter.SearchQuery;
import com.ibrhalil.forgesys.web.filter.SearchRequests;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Flat cross-container app surface (K-15/K-45): {@code ?projectId=} narrows the
 * list; writes without a {@code projectId} land in the default APPS container.
 * Properties/views under {@code /apps/{customAppId}/...}; records in
 * {@link CustomAppRecordController}; container-nested paths in {@link ProjectCustomAppController}.
 */
@RestController
@RequestMapping("/api/v1/custom-apps")
@RequiredArgsConstructor
public class CustomAppController {

    private final CustomAppService customAppService;
    private final PlanLimitService planLimitService;

    /**
     * Plan limits for the CustomApp Builder usage indicators (K-42). Literal segments
     * take precedence over {@code /{id}} path variables regardless of order.
     */
    @GetMapping("/plan-limits")
    @PreAuthorize("hasAuthority('apps:customapp:read')")
    public ResponseEntity<CustomAppPlanLimitsResponse> planLimits() {
        PlanDefinition plan = planLimitService.activePlan();
        return ResponseEntity.ok(new CustomAppPlanLimitsResponse(
                plan.key(), plan.displayName(), plan.maxCustomApps(), plan.maxRecordsPerCustomApp()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('apps:customapp:read')")
    public ResponseEntity<PageResponse<CustomAppResponse>> list(
            @PageableDefault(sort = "name") Pageable pageable,
            SearchQuery searchQuery,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) UUID projectId) {
        if (searchQuery.present()) {
            SearchRequest request = searchQuery.request();
            Pageable sqPageable = SearchRequests.toPageable(request, CustomAppService.FILTER_FIELDS, Sort.by("name"));
            return ResponseEntity.ok(PageResponse.of(customAppService.search(request, sqPageable)));
        }
        SortGuard.require(pageable, CustomAppService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(customAppService.search(q, qFields, projectId, pageable)));
    }

    /** Filter-engine variant of the list: paging + multi-sort + filters + {@code q} in one POST body. */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('apps:customapp:read')")
    public ResponseEntity<PageResponse<CustomAppResponse>> search(@Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, CustomAppService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(customAppService.search(request, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('apps:customapp:read')")
    public ResponseEntity<CustomAppDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(customAppService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('apps:customapp:write')")
    public ResponseEntity<CustomAppResponse> create(@Valid @RequestBody CustomAppRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customAppService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('apps:customapp:write')")
    public ResponseEntity<CustomAppResponse> update(@PathVariable UUID id, @Valid @RequestBody CustomAppRequest request) {
        return ResponseEntity.ok(customAppService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('apps:customapp:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customAppService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
