package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.AuditLog;
import com.ibrhalil.forgesys.persistence.repository.AuditLogRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.web.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records administrative actions to {@code t_audit_logs} (K-19 layer 1). Each entry
 * captures <em>who</em> (actor id + name resolved from the Spring Security context),
 * <em>what</em> (action / entity type / entity id / entity name) and the request
 * metadata (client IP + trace id from {@link RequestContext}).
 *
 * <p><strong>Transaction isolation:</strong> the write runs in a
 * {@link Propagation#REQUIRES_NEW} transaction so it commits independently of the
 * caller's outcome &mdash; even if the audited operation rolls back, the attempt is
 * recorded. The write is best-effort: any failure is logged and swallowed so audit
 * logging can never break the business operation.
 *
 * <p>When there is no authenticated principal (startup, provisioning, background
 * jobs) the actor name falls back to {@link #SYSTEM_ACTOR} and the actor id is null.
 *
 * <p>Old/new value and high-risk request body capture are K-27 extensions and land
 * separately; this is the core <em>who-did-what-to-which-entity</em> record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    /** Actor name recorded when no authenticated principal is present. */
    public static final String SYSTEM_ACTOR = "system";

    private final AuditLogRepository auditLogRepository;

    /**
     * @param action     stable action key, e.g. {@code user_created}
     * @param entityType entity class label, e.g. {@code User}
     * @param entityId   the affected entity's id, or {@code null} for bulk/cross-entity actions
     * @param entityName human-readable entity label (email / name) for the activity feed, or {@code null}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, UUID entityId, String entityName) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setEntityName(entityName);
            resolveActor(entry);
            RequestContext.current().ifPresent(meta -> {
                entry.setIpAddress(meta.clientIp());
                entry.setTraceId(meta.traceId());
            });
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Failed to record audit log (action={}, entityType={}, entityId={})",
                    action, entityType, entityId, ex);
        }
    }

    private void resolveActor(AuditLog entry) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails user) {
            entry.setActorId(user.getUserId());
            entry.setActorName(user.getEmail());
        } else {
            entry.setActorName(SYSTEM_ACTOR);
        }
    }
}
