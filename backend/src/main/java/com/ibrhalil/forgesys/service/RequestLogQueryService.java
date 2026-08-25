package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.RequestLogResponse;
import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.entity.RequestLog;
import com.ibrhalil.forgesys.entity.RequestLog_;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.persistence.repository.RequestLogRepository;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterOperator;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read side of the request/trace log (K-19 layer 3 + K-27). Returns paged views
 * over a tenant's {@code t_request_logs}; the controller guards it with
 * {@code iam:audit:read}.
 */
@Service
@RequiredArgsConstructor
public class RequestLogQueryService {

    /**
     * Filterable/sortable attributes of the request log (K-49 — every displayed
     * column); {@code q} matches {@code path}, {@code traceId}, {@code username},
     * {@code userAgent}. {@code status} is INT-typed (HTTP status code — numeric
     * compare, e.g. GTE 400); {@code requestBody} stays deliberately unregistered
     * (masked high-risk payload, not a filter target).
     */
    public static final FilterFieldSet REQUEST_LOG_FIELDS = FilterFieldSet.builder()
            .field(RequestLog_.TRACE_ID, FilterFieldType.STRING, true)
            .field(RequestLog_.METHOD, FilterFieldType.STRING, false)
            .field(RequestLog_.PATH, FilterFieldType.STRING, true)
            .field(RequestLog_.STATUS, FilterFieldType.INT, false)
            .field(RequestLog_.USER_ID, FilterFieldType.UUID, false)
            .field(RequestLog_.USERNAME, FilterFieldType.STRING, true)
            .field(RequestLog_.IP_ADDRESS, FilterFieldType.STRING, true)
            .field(RequestLog_.USER_AGENT, FilterFieldType.STRING, true)
            .field(RequestLog_.DURATION_MS, FilterFieldType.NUMERIC, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final RequestLogRepository requestLogRepository;

    @Transactional(readOnly = true)
    public Page<RequestLogResponse> findAll(Pageable pageable, String q, List<String> qFields, String traceId,
            String method, Integer status, UUID userId, String username) {
        List<FilterCriteria> filters = new ArrayList<>();
        if (StringUtils.hasText(traceId)) {
            filters.add(new FilterCriteria(RequestLog_.TRACE_ID, FilterOperator.EQ, List.of(traceId.trim())));
        }
        if (StringUtils.hasText(method)) {
            filters.add(new FilterCriteria(RequestLog_.METHOD, FilterOperator.EQ, List.of(method.trim())));
        }
        if (status != null) {
            filters.add(new FilterCriteria(RequestLog_.STATUS, FilterOperator.EQ, List.of(status.toString())));
        }
        if (userId != null) {
            filters.add(new FilterCriteria(RequestLog_.USER_ID, FilterOperator.EQ, List.of(userId.toString())));
        }
        if (StringUtils.hasText(username)) {
            filters.add(new FilterCriteria(RequestLog_.USERNAME, FilterOperator.EQ, List.of(username.trim())));
        }
        return search(pageable, StringUtils.hasText(q) ? q.trim() : null, qFields, filters);
    }

    @Transactional(readOnly = true)
    public Page<RequestLogResponse> search(Pageable pageable, String q, List<String> qFields,
            List<FilterCriteria> filters) {
        Specification<RequestLog> spec = FilterSpecifications.from(REQUEST_LOG_FIELDS, q, qFields, filters);
        return requestLogRepository.findAll(spec, pageable).map(this::toResponse);
    }

    private RequestLogResponse toResponse(RequestLog log) {
        return new RequestLogResponse(
                log.getId(),
                log.getTraceId(),
                log.getMethod(),
                log.getPath(),
                log.getStatus(),
                log.getDurationMs(),
                log.getUserId(),
                log.getUsername(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getRequestBody(),
                log.getCreatedDate());
    }
}