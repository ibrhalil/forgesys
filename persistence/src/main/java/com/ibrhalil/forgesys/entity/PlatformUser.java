package com.ibrhalil.forgesys.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Global platform identity in the public schema (K-50) — NOT a tenant user.
 * HUMAN authenticates with peppered BCrypt; SERVICE authenticates via API keys only.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "passwordHash")
@Table(
        name = "t_platform_users",
        schema = "public",
        indexes = {
                @Index(name = "idx_platform_users_type", columnList = "user_type")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_platform_users_email", columnNames = "email")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class PlatformUser extends GeneratedIdAuditEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 10)
    private PlatformUserType userType;

    /** Null for SERVICE accounts (no password login). */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "token_invalid_before")
    private OffsetDateTime tokenInvalidBefore;
}
