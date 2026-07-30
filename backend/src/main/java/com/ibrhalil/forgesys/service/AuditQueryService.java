package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AuditLogResponse;
import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.dto.LoginHistoryResponse;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.AuditLog;
import com.ibrhalil.forgesys.entity.AuditLog_;
import com.ibrhalil.forgesys.entity.LoginHistory;
import com.ibrhalil.forgesys.entity.LoginHistory_;
import com.ibrhalil.forgesys.persistence.repository.AuditLogRepository;
import com.ibrhalil.forgesys.persistence.repository.LoginHistoryRepository;
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
 * Read side of the audit subsystem (K-19). Returns paged views over a tenant's
 * {@code t_audit_logs} and {@code t_login_history}; the controller guards both with
 * {@code iam:audit:read}. Mapping to response records keeps the entity shape out of
 * the API contract.
 *
 * <p>The GET filter parameters are translated into filter-engine
 * {@link FilterCriteria} clauses and combined (AND) through the shared
 * {@link FilterSpecifications} machinery — no more first-match dispatch, so filters
 * compose and the engine is exercised by real traffic on every audit read.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    /** Filterable/sortable direct attributes of the audit log; {@code q} matches actor/entity names and the action key. */
    public static final FilterFieldSet AUDIT_LOG_FIELDS = FilterFieldSet.builder()
            .field(AuditLog_.ACTION, FilterFieldType.STRING, true)
            .field(AuditLog_.ENTITY_TYPE, FilterFieldType.STRING, false)
            .field(AuditLog_.ENTITY_ID, FilterFieldType.UUID, false)
            .field(AuditLog_.ACTOR_ID, FilterFieldType.UUID, false)
            .field(AuditLog_.ACTOR_NAME, FilterFieldType.STRING, true)
            .field(AuditLog_.ENTITY_NAME, FilterFieldType.STRING, true)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    /** Filterable/sortable direct attributes of the login history; {@code q} matches {@code username}. */
    public static final FilterFieldSet LOGIN_HISTORY_FIELDS = FilterFieldSet.builder()
            .field(LoginHistory_.USER_ID, FilterFieldType.UUID, false)
            .field(LoginHistory_.USERNAME, FilterFieldType.STRING, true)
            .field(LoginHistory_.SUCCESS, FilterFieldType.BOOLEAN, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final AuditLogRepository auditLogRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> findAllAuditLogs(Pageable pageable, String q, String action, UUID actorId) {
        List<FilterCriteria> filters = new ArrayList<>();
        if (StringUtils.hasText(action)) {
            filters.add(new FilterCriteria(AuditLog_.ACTION, FilterOperator.EQ, List.of(action.trim())));
        }
        if (actorId != null) {
            filters.add(new FilterCriteria(AuditLog_.ACTOR_ID, FilterOperator.EQ, List.of(actorId.toString())));
        }
        return searchAuditLogs(pageable, StringUtils.hasText(q) ? q.trim() : null, filters);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> searchAuditLogs(Pageable pageable, String q, List<FilterCriteria> filters) {
        Specification<AuditLog> spec = FilterSpecifications.from(AUDIT_LOG_FIELDS, q, filters);
        return auditLogRepository.findAll(spec, pageable).map(this::toAuditLogResponse);
    }

    @Transactional(readOnly = true)
    public Page<LoginHistoryResponse> findAllLoginHistory(Pageable pageable, String q, UUID userId, Boolean success) {
        List<FilterCriteria> filters = new ArrayList<>();
        if (userId != null) {
            filters.add(new FilterCriteria(LoginHistory_.USER_ID, FilterOperator.EQ, List.of(userId.toString())));
        }
        if (success != null) {
            filters.add(new FilterCriteria(LoginHistory_.SUCCESS, FilterOperator.EQ, List.of(success.toString())));
        }
        return searchLoginHistory(pageable, StringUtils.hasText(q) ? q.trim() : null, filters);
    }

    @Transactional(readOnly = true)
    public Page<LoginHistoryResponse> searchLoginHistory(Pageable pageable, String q, List<FilterCriteria> filters) {
        Specification<LoginHistory> spec = FilterSpecifications.from(LOGIN_HISTORY_FIELDS, q, filters);
        return loginHistoryRepository.findAll(spec, pageable).map(this::toLoginHistoryResponse);
    }

    private AuditLogResponse toAuditLogResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getActorName(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getEntityName(),
                log.getIpAddress(),
                log.getTraceId(),
                log.getCreatedDate());
    }

    private LoginHistoryResponse toLoginHistoryResponse(LoginHistory log) {
        return new LoginHistoryResponse(
                log.getId(),
                log.getUserId(),
                log.getUsername(),
                log.isSuccess(),
                log.getReason(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedDate());
    }
}
