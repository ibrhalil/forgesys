package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.CompanyModulesUpdateRequest;
import com.ibrhalil.forgesys.dto.CompanyReportResponse;
import com.ibrhalil.forgesys.dto.CompanyResponse;
import com.ibrhalil.forgesys.dto.CompanyStatusUpdateRequest;
import com.ibrhalil.forgesys.dto.ModuleResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.dto.SubscriptionResponse;
import com.ibrhalil.forgesys.dto.SubscriptionUpdateRequest;
import com.ibrhalil.forgesys.service.PlatformCompanyService;
import com.ibrhalil.forgesys.web.SortGuard;
import com.ibrhalil.forgesys.web.filter.SearchRequests;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/platform/companies")
@RequiredArgsConstructor
public class PlatformCompanyController {

    /** K-50 F3: platform endpoints accept ONLY platform tokens — tenant JWTs lose access (RISK-18 closure). */
    private static final String PLATFORM_READ =
            "hasAuthority('platform:company:read') and authentication.principal.scope == 'platform'";
    private static final String PLATFORM_WRITE =
            "hasAuthority('platform:company:write') and authentication.principal.scope == 'platform'";
    /** K-50 F4: tenant lifecycle mutations (plan change, module activation). */
    private static final String PLATFORM_LIFECYCLE =
            "hasAuthority('platform:tenant:lifecycle') and authentication.principal.scope == 'platform'";
    /** K-50 F4: cross-tenant usage reports. */
    private static final String PLATFORM_REPORT =
            "hasAuthority('platform:tenant:report') and authentication.principal.scope == 'platform'";

    private final PlatformCompanyService platformCompanyService;

    /** Paged engine-filtered list (K-49); {@code schemaName} is never exposed. */
    @GetMapping
    @PreAuthorize(PLATFORM_READ)
    public ResponseEntity<PageResponse<CompanyResponse>> list(
            @PageableDefault(sort = "name") Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields) {
        SortGuard.require(pageable, PlatformCompanyService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(platformCompanyService.search(q, qFields, pageable)));
    }

    @PostMapping("/search")
    @PreAuthorize(PLATFORM_READ)
    public ResponseEntity<PageResponse<CompanyResponse>> search(@Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, PlatformCompanyService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(platformCompanyService.search(request, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(PLATFORM_READ)
    public CompanyResponse findById(@PathVariable UUID id) {
        return platformCompanyService.findById(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(PLATFORM_WRITE)
    public CompanyResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CompanyStatusUpdateRequest request) {
        return platformCompanyService.updateStatus(id, request.status());
    }

    /* ── K-50 F4: subscription ── */

    @GetMapping("/{id}/subscription")
    @PreAuthorize(PLATFORM_READ)
    public SubscriptionResponse getSubscription(@PathVariable UUID id) {
        return platformCompanyService.getSubscription(id);
    }

    @PutMapping("/{id}/subscription")
    @PreAuthorize(PLATFORM_LIFECYCLE)
    public SubscriptionResponse changePlan(
            @PathVariable UUID id,
            @Valid @RequestBody SubscriptionUpdateRequest request) {
        return platformCompanyService.changePlan(id, request.planKey());
    }

    /* ── K-50 F4: modules ── */

    @GetMapping("/{id}/modules")
    @PreAuthorize(PLATFORM_READ)
    public List<ModuleResponse> getModules(@PathVariable UUID id) {
        return platformCompanyService.getModules(id);
    }

    @PutMapping("/{id}/modules")
    @PreAuthorize(PLATFORM_LIFECYCLE)
    public List<ModuleResponse> updateModules(
            @PathVariable UUID id,
            @Valid @RequestBody CompanyModulesUpdateRequest request) {
        return platformCompanyService.updateModules(id, request);
    }

    /* ── K-50 F4: usage report ── */

    @GetMapping("/{id}/report")
    @PreAuthorize(PLATFORM_REPORT)
    public CompanyReportResponse getReport(@PathVariable UUID id) {
        return platformCompanyService.getReport(id);
    }
}
