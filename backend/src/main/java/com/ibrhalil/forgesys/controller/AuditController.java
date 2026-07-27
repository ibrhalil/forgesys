package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AuditLogResponse;
import com.ibrhalil.forgesys.dto.LoginHistoryResponse;
import com.ibrhalil.forgesys.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only views over the tenant's audit log and login history (K-19). Both endpoints
 * require the {@code iam:audit:read} permission (seeded into the {@code Admin} role) and
 * default to newest-first paging.
 */
@RestController
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping("/api/v1/audit-logs")
    @PreAuthorize("hasAuthority('iam:audit:read')")
    public ResponseEntity<Page<AuditLogResponse>> auditLogs(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId) {
        return ResponseEntity.ok(auditQueryService.findAllAuditLogs(pageable, action, actorId));
    }

    @GetMapping("/api/v1/login-history")
    @PreAuthorize("hasAuthority('iam:audit:read')")
    public ResponseEntity<Page<LoginHistoryResponse>> loginHistory(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Boolean success) {
        return ResponseEntity.ok(auditQueryService.findAllLoginHistory(pageable, userId, success));
    }
}
