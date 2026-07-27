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
@ToString(exclude = {"userAgent"})
@Table(name = "t_login_history", indexes = {
        @jakarta.persistence.Index(name = "idx_login_history_user_id", columnList = "user_id"),
        @jakarta.persistence.Index(name = "idx_login_history_created_at", columnList = "created_at"),
        @jakarta.persistence.Index(name = "idx_login_history_success", columnList = "success")
})
@EntityListeners(AuditingEntityListener.class)
public class LoginHistory extends GeneratedIdAuditEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 150)
    private String username;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 50)
    private String reason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;
}
