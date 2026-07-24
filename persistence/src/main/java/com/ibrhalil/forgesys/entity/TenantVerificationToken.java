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
 * Tenant signup verification token (K-21). Issued when a {@code PROVISIONING} Company is
 * created ({@code createPendingCompany}) and consumed by {@code verifyAndProvision} to
 * promote the Company to {@code ACTIVE} (senkron schema CREATE + Flyway + admin user).
 *
 * <p>Lives in the {@code public} schema (the tenant schema does not exist yet at issue
 * time). Soft-delete-less ({@link GeneratedIdAuditEntity} — no {@code is_deleted}/
 * {@code version}); lifecycle is controlled by {@link #usedAt} + {@link #expiresAt}.
 * A token is single-use: once {@code usedAt} is set it is invalid forever.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "company")
@Table(
        name = "t_tenant_verification_tokens",
        schema = "public",
        indexes = {
                @Index(name = "idx_tenant_verification_tokens_company", columnList = "company_id")
        }
)
public class TenantVerificationToken extends GeneratedIdAuditEntity {

    @Column(nullable = false, length = 255, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tenant_verification_tokens_company"))
    private Company company;

    /**
     * Admin email captured at phase 1 ({@code createPendingCompany}). Phase 2 uses it to
     * create the tenant's first user without re-prompting the user.
     */
    @Column(name = "admin_email", nullable = false, length = 150)
    private String adminEmail;

    /**
     * Admin password, pre-hashed at phase 1 by {@code PepperingPasswordEncoder}. Phase 2
     * stores it verbatim on the new {@code User} (no re-hash). The raw password never
     * persists.
     */
    @Column(name = "admin_password_hash", nullable = false, length = 255)
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
