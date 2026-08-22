package com.ibrhalil.forgesys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"requestBody"})
@Table(name = "t_request_logs", indexes = {
        @Index(name = "idx_request_logs_trace_id", columnList = "trace_id"),
        @Index(name = "idx_request_logs_user_id", columnList = "user_id"),
        @Index(name = "idx_request_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_request_logs_path", columnList = "path"),
        @Index(name = "idx_request_logs_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
public class RequestLog extends GeneratedIdAuditEntity {

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "path", length = 500)
    private String path;

    @Column(name = "status")
    private Integer status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", length = 150)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_body", columnDefinition = "jsonb")
    private String requestBody;
}