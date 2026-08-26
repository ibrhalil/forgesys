package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.PlatformAuditLogResponse;
import com.ibrhalil.forgesys.service.PlatformAuditQueryService;
import com.ibrhalil.forgesys.web.SortGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** K-50 F7 platform audit trail read — platform tokens only (scope == 'platform'). */
@RestController
@RequestMapping("/api/v1/platform/audit-logs")
@RequiredArgsConstructor
public class PlatformAuditController {

    private static final String PLATFORM_AUDIT_READ =
            "hasAuthority('platform:audit:read') and authentication.principal.scope == 'platform'";

    private final PlatformAuditQueryService platformAuditQueryService;

    @GetMapping
    @PreAuthorize(PLATFORM_AUDIT_READ)
    public ResponseEntity<PageResponse<PlatformAuditLogResponse>> list(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) OffsetDateTime fromDate,
            @RequestParam(required = false) OffsetDateTime toDate) {
        SortGuard.require(pageable, PlatformAuditQueryService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(platformAuditQueryService.findAll(
                pageable, q, qFields, action, actorId, targetType, fromDate, toDate)));
    }
}
