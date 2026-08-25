package com.ibrhalil.forgesys.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Tenant-internal single-use auth token (user lifecycle): email verification and
 * password reset. Mirrors the {@code TenantVerificationToken} ([RISK-30]) conventions —
 * hash-at-rest, soft-delete-less {@link GeneratedIdAuditEntity}, {@code usedAt}
 * invalidation — but lives in the TENANT schema and points at a {@link User}.
 *
 * <p>Only the SHA-256 hex digest of the raw token is persisted ({@link #tokenHash});
 * the raw value exists solely in the mailed link. Re-issuing a purpose supersedes the
 * user's outstanding tokens of the same purpose (stamped {@code usedAt}), so only the
 * newest link works.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"user", "tokenHash"})
@Table(
        name = "t_auth_tokens",
        indexes = {
                @Index(name = "idx_auth_tokens_user_purpose", columnList = "user_id, purpose")
        }
)
public class UserAuthToken extends GeneratedIdAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_auth_tokens_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserAuthTokenPurpose purpose;

    /** SHA-256 hex digest of the raw token — never the raw value ([RISK-30]). */
    @Column(name = "token_hash", nullable = false, length = 255, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime expiresAt;

    @Column(name = "used_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime usedAt;

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return expiresAt.isBefore(now) || expiresAt.isEqual(now);
    }
}
