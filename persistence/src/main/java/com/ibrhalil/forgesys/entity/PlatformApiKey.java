package com.ibrhalil.forgesys.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * API key of a SERVICE platform identity (K-50). Stores only the SHA-256 digest of
 * the secret (TokenHasher / RISK-30 pattern) — the raw {@code <prefix>_<secret>}
 * value is shown exactly once at creation. Scopes are comma-separated platform
 * permission names (registry lives in code).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"keyHash", "platformUser"})
@Table(
        name = "t_platform_api_keys",
        schema = "public",
        indexes = {
                @Index(name = "idx_platform_api_keys_user", columnList = "platform_user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_platform_api_keys_prefix", columnNames = "key_prefix"),
                @UniqueConstraint(name = "uk_platform_api_keys_hash", columnNames = "key_hash")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class PlatformApiKey extends GeneratedIdAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platform_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_platform_api_keys_user"))
    private PlatformUser platformUser;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 255)
    private String keyHash;

    @Column(nullable = false, columnDefinition = "text")
    private String scopes;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    /** Revocation timestamp — null while the key is usable. */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;
}
