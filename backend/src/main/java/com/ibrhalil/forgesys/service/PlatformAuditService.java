package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.PlatformAuditLog;
import com.ibrhalil.forgesys.persistence.repository.PlatformAuditLogRepository;
import com.ibrhalil.forgesys.web.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * K-50 append-only platform audit trail ({@code t_platform_audit_logs}): platform
 * logins, API-key lifecycle, tenant lifecycle actions, switch/impersonation events.
 * REQUIRES_NEW + best-effort (LoginHistoryService pattern) — the write never breaks
 * the business op. Platform services must NOT use the tenant {@code @AuditLog} AOP
 * (it writes the tenant-schema {@code t_audit_logs}, which does not exist in
 * {@code public} on PostgreSQL).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAuditService {

    /** Actor kind recorded in {@code actor_type}; bootstrap/unattributable events use SYSTEM. */
    public static final String ACTOR_HUMAN = "HUMAN";
    public static final String ACTOR_SERVICE = "SERVICE";
    public static final String ACTOR_SYSTEM = "SYSTEM";

    public static final String ACTION_LOGIN_SUCCESS = "platform_login_success";
    public static final String ACTION_LOGIN_FAILED = "platform_login_failed";

    private final PlatformAuditLogRepository platformAuditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String actorType, String action,
                       String targetType, UUID targetId, String detail) {
        try {
            PlatformAuditLog entry = new PlatformAuditLog();
            entry.setActorId(actorId);
            entry.setActorType(actorType != null ? actorType : ACTOR_SYSTEM);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail);
            RequestContext.current().ifPresent(meta -> {
                entry.setIpAddress(meta.clientIp());
                entry.setTraceId(meta.traceId());
            });
            platformAuditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Failed to record platform audit entry (action={}, actorId={})", action, actorId, ex);
        }
    }
}
