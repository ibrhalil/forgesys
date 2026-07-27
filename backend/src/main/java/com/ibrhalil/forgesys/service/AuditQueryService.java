package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AuditLogResponse;
import com.ibrhalil.forgesys.dto.LoginHistoryResponse;
import com.ibrhalil.forgesys.entity.AuditLog;
import com.ibrhalil.forgesys.entity.LoginHistory;
import com.ibrhalil.forgesys.persistence.repository.AuditLogRepository;
import com.ibrhalil.forgesys.persistence.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Read side of the audit subsystem (K-19). Returns paged views over a tenant's
 * {@code t_audit_logs} and {@code t_login_history}; the controller guards both with
 * {@code iam:audit:read}. Mapping to response records keeps the entity shape out of
 * the API contract.
 *
 * <p>Optional filters: audit-logs by {@code action} or {@code actorId}, login-history by
 * {@code userId} or {@code success}. Filters are evaluated with first-match priority (a
 * single active filter at a time); combining several into one query is future work
 * (Specification-based). Date-range filtering can be added on top of the existing
 * {@code findByDateRange} query.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> findAllAuditLogs(Pageable pageable, String action, UUID actorId) {
        Page<AuditLog> page;
        if (StringUtils.hasText(action)) {
            page = auditLogRepository.findByAction(action, pageable);
        } else if (actorId != null) {
            page = auditLogRepository.findByActorId(actorId, pageable);
        } else {
            page = auditLogRepository.findAll(pageable);
        }
        return page.map(this::toAuditLogResponse);
    }

    @Transactional(readOnly = true)
    public Page<LoginHistoryResponse> findAllLoginHistory(Pageable pageable, UUID userId, Boolean success) {
        Page<LoginHistory> page;
        if (userId != null) {
            page = loginHistoryRepository.findByUserId(userId, pageable);
        } else if (success != null) {
            page = loginHistoryRepository.findBySuccess(success, pageable);
        } else {
            page = loginHistoryRepository.findAll(pageable);
        }
        return page.map(this::toLoginHistoryResponse);
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
