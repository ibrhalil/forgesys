package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AuditLogResponse;
import com.ibrhalil.forgesys.dto.LoginHistoryResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.RequestLogResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.service.AuditQueryService;
import com.ibrhalil.forgesys.service.RequestLogQueryService;
import com.ibrhalil.forgesys.web.SortGuard;
import com.ibrhalil.forgesys.web.filter.SearchRequests;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only views over the tenant's audit log, login history and request/trace log (K-19 + K-27).
 * All endpoints require the {@code iam:audit:read} permission (seeded into the {@code Admin} role)
 * and default to newest-first paging. GET parameters are translated into filter-engine
 * criteria by the respective query services (composable AND filters); each surface also
 * carries a full filter-engine {@code POST /search} variant (K-49).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueryService auditQueryService;
    private final RequestLogQueryService requestLogQueryService;

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('iam:audit:read')")
    public ResponseEntity<PageResponse<AuditLogResponse>> auditLogs(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId) {
        SortGuard.require(pageable, AuditQueryService.AUDIT_LOG_FIELDS);
        return ResponseEntity.ok(PageResponse.of(
                auditQueryService.findAllAuditLogs(pageable, q, qFields, action, actorId)));
    }

    @PostMapping("/audit-logs/search")
    @PreAuthorize("hasAuthority('iam:audit:read')")
    public ResponseEntity<PageResponse<AuditLogResponse>> searchAuditLogs(
            @Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, AuditQueryService.AUDIT_LOG_FIELDS);
        return ResponseEntity.ok(PageResponse.of(
                auditQueryService.searchAuditLogs(pageable, request.q(), request.qFields(), request.filters())));
    }

    @GetMapping("/login-history")
    @PreAuthorize("hasAuthority('iam:audit:read')")
    public ResponseEntity<PageResponse<LoginHistoryResponse>> loginHistory(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Boolean success) {
        SortGuard.require(pageable, AuditQueryService.LOGIN_HISTORY_FIELDS);
        return ResponseEntity.ok(PageResponse.of(
                auditQueryService.findAllLoginHistory(pageable, q, qFields, userId, success)));
    }

    @PostMapping("/login-history/search")
    @PreAuthorize("hasAuthority('iam:audit:read')")
    public ResponseEntity<PageResponse<LoginHistoryResponse>> searchLoginHistory(
            @Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, AuditQueryService.LOGIN_HISTORY_FIELDS);
        return ResponseEntity.ok(PageResponse.of(
                auditQueryService.searchLoginHistory(pageable, request.q(), request.qFields(), request.filters())));
    }

    @GetMapping("/request-logs")
    @PreAuthorize("hasAuthority('iam:audit:read')")
    public ResponseEntity<PageResponse<RequestLogResponse>> requestLogs(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String username) {
        SortGuard.require(pageable, RequestLogQueryService.REQUEST_LOG_FIELDS);
        return ResponseEntity.ok(PageResponse.of(
                requestLogQueryService.findAll(pageable, q, qFields, traceId, method, status, userId, username)));
    }

    @PostMapping("/request-logs/search")
    @PreAuthorize("hasAuthority('iam:audit:read')")
    public ResponseEntity<PageResponse<RequestLogResponse>> searchRequestLogs(
            @Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, RequestLogQueryService.REQUEST_LOG_FIELDS);
        return ResponseEntity.ok(PageResponse.of(
                requestLogQueryService.search(pageable, request.q(), request.qFields(), request.filters())));
    }
}
