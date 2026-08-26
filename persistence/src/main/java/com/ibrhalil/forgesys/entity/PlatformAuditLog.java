package com.ibrhalil.forgesys.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Append-only platform audit trail (K-50) — platform logins, API-key lifecycle,
 * tenant lifecycle actions, switch/impersonation events. DB trigger rejects
 * UPDATE/DELETE (public V4). {@code actorType}: HUMAN | SERVICE | SYSTEM
 * (SYSTEM = bootstrap/unattributable events).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "detail")
@Table(
        name = "t_platform_audit_logs",
        schema = "public",
        indexes = {
                @Index(name = "idx_platform_audit_logs_actor_id", columnList = "actor_id"),
                @Index(name = "idx_platform_audit_logs_action", columnList = "action"),
                @Index(name = "idx_platform_audit_logs_target", columnList = "target_type, target_id"),
                @Index(name = "idx_platform_audit_logs_created_at", columnList = "created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class PlatformAuditLog extends GeneratedIdAuditEntity {

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, length = 10)
    private String actorType;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "target_type", length = 100)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "trace_id", length = 100)
    private String traceId;
}
