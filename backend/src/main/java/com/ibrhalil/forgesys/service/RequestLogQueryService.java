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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Read side of the request/trace log (K-19 layer 3 + K-27); {@code iam:audit:read} in the controller. */
@Service
@RequiredArgsConstructor
public class RequestLogQueryService {

    /**
     * Filterable/sortable request-log attributes (K-49); {@code q} matches path,
     * traceId, username, userAgent. {@code status} is INT (numeric compare, e.g.
     * GTE 400); {@code requestBody} deliberately unregistered (masked payload).
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
        return search(pageable, StringUtils.hasText(q) ? q.trim() : null, qFields,
                scopedFilters(traceId, method, status, userId, username));
    }

    /** CSV export over the legacy scoped flat params (K-55 F5) — same pipeline as the {@code sq} variant. */
    @Transactional(readOnly = true)
    public String exportCsvScoped(Pageable pageable, String q, List<String> qFields, String traceId,
            String method, Integer status, UUID userId, String username) {
        return exportCsv(pageable, StringUtils.hasText(q) ? q.trim() : null, qFields,
                scopedFilters(traceId, method, status, userId, username));
    }

    private static List<FilterCriteria> scopedFilters(String traceId, String method, Integer status, UUID userId,
            String username) {
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
        return filters;
    }

    @Transactional(readOnly = true)
    public Page<RequestLogResponse> search(Pageable pageable, String q, List<String> qFields,
            List<FilterCriteria> filters) {
        Specification<RequestLog> spec = FilterSpecifications.from(REQUEST_LOG_FIELDS, q, qFields, filters);
        return requestLogRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /** Export row cap — bounded work per request, documented in the endpoint contract (K-55 F5). */
    public static final int MAX_EXPORT_ROWS = 10_000;

    /** Fixed-English CSV headers — machine data, deliberately not localized (K-55 F5). */
    private static final String CSV_HEADER = "id,traceId,method,path,status,durationMs,userId,username,ipAddress,userAgent,createdAt";

    /**
     * CSV export over the same filter pipeline as the list (K-55 F5). RFC 4180
     * escaping (quote-wrap on separators/quotes/newlines, quotes doubled). The
     * masked {@code requestBody} is deliberately NOT exported — payload size and
     * multiline bodies would dwarf the tabular value.
     */
    @Transactional(readOnly = true)
    public String exportCsv(Pageable pageable, String q, List<String> qFields, List<FilterCriteria> filters) {
        Pageable capped = PageRequest.of(0, Math.min(pageable.getPageSize(), MAX_EXPORT_ROWS), pageable.getSort());
        List<RequestLogResponse> rows = search(capped, q, qFields, filters).getContent();
        StringBuilder sb = new StringBuilder(CSV_HEADER.length() + 64 * rows.size());
        sb.append(CSV_HEADER);
        for (RequestLogResponse r : rows) {
            sb.append('\n')
                    .append(csv(r.id() == null ? "" : r.id().toString())).append(',')
                    .append(csv(r.traceId())).append(',')
                    .append(csv(r.method())).append(',')
                    .append(csv(r.path())).append(',')
                    .append(r.status() == null ? "" : String.valueOf(r.status())).append(',')
                    .append(r.durationMs() == null ? "" : String.valueOf(r.durationMs())).append(',')
                    .append(csv(r.userId() == null ? "" : r.userId().toString())).append(',')
                    .append(csv(r.username())).append(',')
                    .append(csv(r.ipAddress())).append(',')
                    .append(csv(r.userAgent())).append(',')
                    .append(r.createdAt() == null ? "" : r.createdAt().toString());
        }
        return sb.toString();
    }

    private static String csv(String value) {
        if (value == null) return "";
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
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