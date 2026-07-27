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

/**
 * Read side of the audit subsystem (K-19). Returns paged views over a tenant's
 * {@code t_audit_logs} and {@code t_login_history}; the controller guards both with
 * {@code iam:audit:read}. Mapping to response records keeps the entity shape out of
 * the API contract.
 *
 * <p>No filtering yet &mdash; a v1 browse (newest first). Action / userId / date-range
 * filters are a follow-up; the repositories already expose the derived queries.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> findAllAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(this::toAuditLogResponse);
    }

    @Transactional(readOnly = true)
    public Page<LoginHistoryResponse> findAllLoginHistory(Pageable pageable) {
        return loginHistoryRepository.findAll(pageable).map(this::toLoginHistoryResponse);
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
