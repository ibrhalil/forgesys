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
    private final ObjectProvider<AuditService> self;

    /**
     * @param action     stable action key, e.g. {@code user_created}
     * @param entityType entity class label, e.g. {@code User}
     * @param entityId   the affected entity's id, or {@code null} for bulk/cross-entity actions
     * @param entityName human-readable entity label (email / name) for the activity feed, or {@code null}
     */
    public void record(String action, String entityType, UUID entityId, String entityName) {
        recordDelta(action, entityType, entityId, entityName, null, null);
    }

    /**
     * Faz 2b delta capture: like {@link #record} but also persists the before/after state
     * (as JSON strings in {@code old_value}/{@code new_value}) so the audit answers
     * <em>"who granted/revoked which permission to whom"</em>, not just the action key.
     * {@code oldValue}/{@code newValue} are caller-built JSON (see {@link #namesJson});
     * {@code null} leaves a column unset. Use the plain {@link #record} overload when no
     * delta applies (create/delete/status).
     */
    public void recordDelta(String action, String entityType, UUID entityId, String entityName,
                            String oldValue, String newValue) {
        try {
            self.getObject().recordInNewTx(action, entityType, entityId, entityName, oldValue, newValue);
        } catch (RuntimeException ex) {
            log.warn("Failed to record audit log (action={}, entityType={}, entityId={})",
                    action, entityType, entityId, ex);
        }
    }

    /**
     * Isolated write invoked through the {@code self} proxy so its REQUIRES_NEW
     * commit (JPA flushes at commit, after the method body returns) is covered by
     * {@link #record}'s try/catch, not the caller's transaction.
     */
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

    /**
     * Faz 2b: deterministic JSON array of names for the {@code old_value}/{@code new_value}
     * audit columns (sorted, escaped). Dependency-free (names are simple role/permission/
     * group strings); callers pass the before/after name collections so the audit record
     * shows the exact privilege delta, not just the action key.
     */
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
