package com.ibrhalil.forgesys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Email domain owned by an organization (K-21 refactor). 1:N — an org may own several
 * domains ({@code geba.com}, {@code geba.net}, {@code klup-geba.edu}). Optional: an org
 * can be created with zero domains (student-club / personal scenario).
 *
 * <p>Drives the self-register allow-list (Epic 2.9 — register is open only for emails
 * whose domain matches a {@code verified=true} row) and is the anchor for future
 * LDAP/SSO wiring (enterprise phase). Custom-domain verification flow (DNS TXT/MX) is
 * deferred — until it lands every row is {@code verified=false} and self-register is
 * closed (invite-only).
 *
 * <p>Lives in the {@code public} schema (must be readable from any tenant context for
 * register checks). Soft-delete + partial unique index on {@code domain} (RISK-17) so a
 * deleted domain is reusable.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "company")
@Table(
        name = "t_organization_domains",
        schema = "public",
        indexes = {
                @Index(name = "idx_organization_domains_company", columnList = "company_id"),
                @Index(name = "idx_organization_domains_domain", columnList = "domain")
        }
)
@SQLDelete(sql = "UPDATE t_organization_domains SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class OrganizationDomain extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_organization_domains_company"))
    private Company company;

    @Column(nullable = false, length = 150, unique = true)
    private String domain;

    @Column(nullable = false)
    private boolean verified = false;

    /**
     * Reserved for the custom-domain verification flow (deferred). {@code DNS_TXT} or
     * {@code MX}; {@code null} while not yet driven through verification. CHECK
     * constraint enforced at the DB level (see {@code public/V3} migration).
     */
    @Column(name = "verification_method", length = 50)
    private String verificationMethod;
}
