package com.ibrhalil.forgesys.entity;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"oldValue", "newValue", "requestBody"})
@Table(name = "t_audit_logs", indexes = {
        @jakarta.persistence.Index(name = "idx_audit_logs_actor_id", columnList = "actorId"),
        @jakarta.persistence.Index(name = "idx_audit_logs_entity", columnList = "entityType, entityId"),
        @jakarta.persistence.Index(name = "idx_audit_logs_action", columnList = "action"),
        @jakarta.persistence.Index(name = "idx_audit_logs_created_at", columnList = "createdDate")
})
@EntityListeners(AuditingEntityListener.class)
public class AuditLog extends GeneratedIdAuditEntity {

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", nullable = false, length = 200)
    private String actorName;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_name", length = 200)
    private String entityName;

    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    @Column(name = "request_body", columnDefinition = "jsonb")
    private String requestBody;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "trace_id", length = 100)
    private String traceId;
}
