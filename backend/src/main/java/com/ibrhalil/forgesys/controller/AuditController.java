package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AuditLogResponse;
import com.ibrhalil.forgesys.dto.LoginHistoryResponse;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.RequestLogResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.service.AuditQueryService;
import com.ibrhalil.forgesys.service.RequestLogQueryService;
import com.ibrhalil.forgesys.web.SortGuard;
import com.ibrhalil.forgesys.web.filter.SearchQuery;
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
 * Read-only views over the tenant's audit log, login history and request log
 * (K-19/K-27), all behind {@code iam:audit:read}, newest-first. GET params are
 * translated into filter-engine criteria by the query services; each surface
 * also carries a full filter-engine {@code POST /search} variant (K-49).
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
            SearchQuery searchQuery,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId) {
        SortGuard.require(pageable, AuditQueryService.AUDIT_LOG_FIELDS);
        if (searchQuery.present()) {
            SearchRequest request = searchQuery.request();
            return ResponseEntity.ok(PageResponse.of(
                    auditQueryService.searchAuditLogs(pageable, request.q(), request.qFields(), request.filters())));
        }
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
            SearchQuery searchQuery,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Boolean success) {
        SortGuard.require(pageable, AuditQueryService.LOGIN_HISTORY_FIELDS);
        if (searchQuery.present()) {
            SearchRequest request = searchQuery.request();
            return ResponseEntity.ok(PageResponse.of(
                    auditQueryService.searchLoginHistory(pageable, request.q(), request.qFields(), request.filters())));
        }
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
            SearchQuery searchQuery,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String username) {
        SortGuard.require(pageable, RequestLogQueryService.REQUEST_LOG_FIELDS);
        if (searchQuery.present()) {
            SearchRequest request = searchQuery.request();
            return ResponseEntity.ok(PageResponse.of(
                    requestLogQueryService.search(pageable, request.q(), request.qFields(), request.filters())));
        }
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

    /**
     * CSV export over the same filter pipeline as the list (K-55 F5): UTF-8 with a
     * BOM (Excel-safe Turkish), RFC 4180 escaping, ≤ {@code MAX_EXPORT_ROWS} rows.
     * Audited — who exported what filter matters more than the data itself.
     */
    @GetMapping("/request-logs/export")
    @PreAuthorize("hasAuthority('iam:audit:read')")
    @com.ibrhalil.forgesys.audit.AuditLog(action = "request_logs_exported", entityType = "RequestLog")
    public ResponseEntity<byte[]> exportRequestLogs(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC, size = RequestLogQueryService.MAX_EXPORT_ROWS) Pageable pageable,
            SearchQuery searchQuery,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String username) {
        SortGuard.require(pageable, RequestLogQueryService.REQUEST_LOG_FIELDS);
        String csv;
        if (searchQuery.present()) {
            SearchRequest request = searchQuery.request();
            csv = requestLogQueryService.exportCsv(pageable, request.q(), request.qFields(), request.filters());
        } else {
            csv = requestLogQueryService.exportCsvScoped(pageable, q, qFields, traceId, method, status, userId, username);
        }
        byte[] body = ('\uFEFF' + csv).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "request-logs-" + java.time.Instant.now().toString().replace(':', '-') + ".csv";
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv;charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
