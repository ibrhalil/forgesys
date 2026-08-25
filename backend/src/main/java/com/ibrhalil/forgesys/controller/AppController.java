package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AppDetailResponse;
import com.ibrhalil.forgesys.dto.AppPlanLimitsResponse;
import com.ibrhalil.forgesys.dto.AppRequest;
import com.ibrhalil.forgesys.dto.AppResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.service.AppBuilderService;
import com.ibrhalil.forgesys.service.PlanLimitService;
import com.ibrhalil.forgesys.web.SortGuard;
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

/**
 * Flat custom-app surface (K-15, re-scoped by K-45): the cross-container list —
 * {@code ?projectId=} narrows it to one APPS container. Writes without an explicit
 * {@code projectId} land in the tenant's default APPS container. Properties/views are
 * nested under {@code /apps/{appId}/...} (see {@link AppPropertyController} /
 * {@link AppViewController}); records under {@link AppRecordController};
 * container-nested reads/writes on {@link ProjectAppController}.
 */
@RestController
@RequestMapping("/api/v1/apps")
@RequiredArgsConstructor
public class AppController {

    private final AppBuilderService appBuilderService;
    private final PlanLimitService planLimitService;

    /**
     * Plan limits for the App Builder usage indicators (K-42). Declared BEFORE the
     * {@code /{id}} mapping for readability — Spring MVC gives literal segments
     * precedence over path variables regardless of order.
     */
    @GetMapping("/plan-limits")
    @PreAuthorize("hasAuthority('apps:app:read')")
    public ResponseEntity<AppPlanLimitsResponse> planLimits() {
        PlanDefinition plan = planLimitService.activePlan();
        return ResponseEntity.ok(new AppPlanLimitsResponse(
                plan.key(), plan.displayName(), plan.maxApps(), plan.maxRecordsPerApp()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('apps:app:read')")
    public ResponseEntity<PageResponse<AppResponse>> list(
            @PageableDefault(sort = "name") Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) UUID projectId) {
        SortGuard.require(pageable, AppBuilderService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(appBuilderService.search(q, qFields, projectId, pageable)));
    }

    /**
     * Filter-engine variant of the list: paging + multi-sort + structured filters +
     * global {@code q} (optionally narrowed via {@code qFields}) in one POST body.
     */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('apps:app:read')")
    public ResponseEntity<PageResponse<AppResponse>> search(@Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, AppBuilderService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(appBuilderService.search(request, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('apps:app:read')")
    public ResponseEntity<AppDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(appBuilderService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('apps:app:write')")
    public ResponseEntity<AppResponse> create(@Valid @RequestBody AppRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appBuilderService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('apps:app:write')")
    public ResponseEntity<AppResponse> update(@PathVariable UUID id, @Valid @RequestBody AppRequest request) {
        return ResponseEntity.ok(appBuilderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('apps:app:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        appBuilderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
