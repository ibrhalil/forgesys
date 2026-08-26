package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.AuditLog;
import com.ibrhalil.forgesys.persistence.repository.AuditLogRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.web.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes admin actions to {@code t_audit_logs} (K-19 layer 1): who (SecurityContext),
 * what (action/entity), request metadata (IP + traceId from {@link RequestContext}).
 * REQUIRES_NEW + best-effort — the write commits even when the business op rolls
 * back and never breaks it. No authenticated principal → actor {@link #SYSTEM_ACTOR}.
 * Rationale: docs/CODE_NOTES.md (backend/service → AuditService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    /** Actor name recorded when no authenticated principal is present. */
    public static final String SYSTEM_ACTOR = "system";

    private final AuditLogRepository auditLogRepository;
    private final ObjectProvider<AuditService> self;

    public void record(String action, String entityType, UUID entityId, String entityName) {
        recordDelta(action, entityType, entityId, entityName, null, null);
    }

    /**
     * K-50 F6: explicit actor (a platform identity writing into the tenant audit
     * trail during a switch) instead of the SecurityContext principal.
     */
    public void recordWithActor(String action, String entityType, UUID entityId, String entityName,
                                UUID actorId, String actorName) {
        try {
            self.getObject().recordInNewTxWithActor(action, entityType, entityId, entityName, actorId, actorName);
        } catch (RuntimeException ex) {
            log.warn("Failed to record audit log (action={}, entityType={}, entityId={})",
                    action, entityType, entityId, ex);
        }
    }

    /** Like {@link #record}, plus the before/after delta (JSON via {@link #namesJson}). */
    public void recordDelta(String action, String entityType, UUID entityId, String entityName,
                            String oldValue, String newValue) {
        try {
            self.getObject().recordInNewTx(action, entityType, entityId, entityName, oldValue, newValue);
        } catch (RuntimeException ex) {
            log.warn("Failed to record audit log (action={}, entityType={}, entityId={})",
                    action, entityType, entityId, ex);
        }
    }

    /** Isolated write via the self proxy — the flush-at-commit lands inside record's try/catch. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTx(String action, String entityType, UUID entityId, String entityName,
                              String oldValue, String newValue) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setEntityName(entityName);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        resolveActor(entry);
        RequestContext.current().ifPresent(meta -> {
            entry.setIpAddress(meta.clientIp());
            entry.setTraceId(meta.traceId());
        });
        auditLogRepository.save(entry);
    }

    /** K-50 F6 twin of {@link #recordInNewTx} with the actor supplied by the caller. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTxWithActor(String action, String entityType, UUID entityId, String entityName,
                                       UUID actorId, String actorName) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setEntityName(entityName);
        entry.setActorId(actorId);
        entry.setActorName(actorName != null ? actorName : SYSTEM_ACTOR);
        RequestContext.current().ifPresent(meta -> {
            entry.setIpAddress(meta.clientIp());
            entry.setTraceId(meta.traceId());
        });
        auditLogRepository.save(entry);
    }

    /** Deterministic sorted+escaped JSON array of names for the old/new value columns. */
    public static String namesJson(java.util.Collection<String> names) {
        if (names == null) {
            return null;
        }
        java.util.TreeSet<String> sorted = new java.util.TreeSet<>(names);
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String name : sorted) {
            if (name == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(name.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
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
