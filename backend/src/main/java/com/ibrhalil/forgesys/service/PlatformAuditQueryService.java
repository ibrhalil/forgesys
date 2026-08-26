package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.dto.PlatformAuditLogResponse;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.PlatformAuditLog;
import com.ibrhalil.forgesys.entity.PlatformAuditLog_;
import com.ibrhalil.forgesys.persistence.repository.PlatformAuditLogRepository;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read side of the platform audit trail (K-50 F7): paged view over
 * {@code t_platform_audit_logs} ({@code platform:audit:read} in the controller).
 * GET params are translated into filter-engine {@link FilterCriteria} clauses and
 * AND-combined (AuditQueryService pattern — no full search endpoint in v1).
 */
@Service
@RequiredArgsConstructor
public class PlatformAuditQueryService {

    /** Filterable/sortable platform-audit attributes; {@code q} matches action, target type, IP, trace id. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(PlatformAuditLog_.ACTION, FilterFieldType.STRING, true)
            .field(PlatformAuditLog_.ACTOR_ID, FilterFieldType.UUID, false)
            .field(PlatformAuditLog_.ACTOR_TYPE, FilterFieldType.STRING, false)
            .field(PlatformAuditLog_.TARGET_TYPE, FilterFieldType.STRING, true)
            .field(PlatformAuditLog_.TARGET_ID, FilterFieldType.UUID, false)
            .field(PlatformAuditLog_.IP_ADDRESS, FilterFieldType.STRING, true)
            .field(PlatformAuditLog_.TRACE_ID, FilterFieldType.STRING, true)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .build();

    private final PlatformAuditLogRepository platformAuditLogRepository;

    @Transactional(readOnly = true)
    public Page<PlatformAuditLogResponse> findAll(Pageable pageable, String q, List<String> qFields,
            String action, UUID actorId, String targetType, OffsetDateTime fromDate, OffsetDateTime toDate) {
        List<FilterCriteria> filters = new ArrayList<>();
        if (StringUtils.hasText(action)) {
            filters.add(new FilterCriteria(PlatformAuditLog_.ACTION, FilterOperator.EQ, List.of(action.trim())));
        }
        if (actorId != null) {
            filters.add(new FilterCriteria(PlatformAuditLog_.ACTOR_ID, FilterOperator.EQ,
                    List.of(actorId.toString())));
        }
        if (StringUtils.hasText(targetType)) {
            filters.add(new FilterCriteria(PlatformAuditLog_.TARGET_TYPE, FilterOperator.EQ,
                    List.of(targetType.trim())));
        }
        if (fromDate != null) {
            filters.add(new FilterCriteria(AuditEntity_.CREATED_DATE, FilterOperator.GTE,
                    List.of(fromDate.toString())));
        }
        if (toDate != null) {
            filters.add(new FilterCriteria(AuditEntity_.CREATED_DATE, FilterOperator.LTE,
                    List.of(toDate.toString())));
        }
        Specification<PlatformAuditLog> spec =
                FilterSpecifications.from(FILTER_FIELDS, StringUtils.hasText(q) ? q.trim() : null, qFields, filters);
        return platformAuditLogRepository.findAll(spec, pageable).map(PlatformAuditQueryService::toResponse);
    }

    private static PlatformAuditLogResponse toResponse(PlatformAuditLog entry) {
        return new PlatformAuditLogResponse(
                entry.getId(),
                entry.getActorId(),
                entry.getActorType(),
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getDetail(),
                entry.getIpAddress(),
                entry.getTraceId(),
                entry.getCreatedDate());
    }
}
