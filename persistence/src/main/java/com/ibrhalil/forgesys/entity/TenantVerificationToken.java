package com.ibrhalil.forgesys.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Tenant signup verification token (K-21), {@code public} schema (the tenant schema
 * does not exist at issue time). Single-use ({@link #usedAt} + {@link #expiresAt});
 * {@link #token} stores only the SHA-256 digest (RISK-30) — the raw value lives
 * solely in the emailed link.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"company", "token", "adminPasswordHash"})
@Table(
        name = "t_tenant_verification_tokens",
        schema = "public",
        indexes = {
                @Index(name = "idx_tenant_verification_tokens_company", columnList = "company_id")
        }
)
public class TenantVerificationToken extends GeneratedIdAuditEntity {

    /** SHA-256 hex digest of the raw token (RISK-30) — a DB leak cannot replay a signup link. */
    @Column(nullable = false, length = 255, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tenant_verification_tokens_company"))
    private Company company;

    /** Admin email captured at phase 1; phase 2 creates the tenant's first user from it. */
    @Column(name = "admin_email", nullable = false, length = 150)
    private String adminEmail;

    /**
     * Admin password, pre-hashed at phase 1 ({@code PepperingPasswordEncoder}) and
     * stored verbatim on the new {@code User} in phase 2; the raw password never
     * persists. Nulled by {@code verifyAndProvision} once the admin exists (nullable
     * since public V3).
     */
    @Column(name = "admin_password_hash", length = 255)
    private String adminPasswordHash;

    @Column(name = "admin_first_name", length = 100)
    private String adminFirstName;

    @Column(name = "admin_last_name", length = 100)
    private String adminLastName;

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
