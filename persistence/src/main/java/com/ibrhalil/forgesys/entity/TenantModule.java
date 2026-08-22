package com.ibrhalil.forgesys.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.SQLDelete;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Records that a module (identified by {@code moduleKey}, resolved against the code-side
 * {@code ModuleDefinition} registry) is activated for a tenant (K-16 / Epic 3.0.A).
 * Partial unique {@code (company_id, module_key) WHERE is_deleted = false}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"company"})
@Table(
        name = "t_tenant_modules",
        schema = "public",
        indexes = {
                @Index(name = "idx_tenant_modules_company", columnList = "company_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tenant_modules_company_module", columnNames = {"company_id", "module_key"})
        }
)
@SQLDelete(sql = "UPDATE t_tenant_modules SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class TenantModule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tenant_modules_company"))
    private Company company;

    @Column(name = "module_key", nullable = false, length = 50)
    private String moduleKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModuleStatus status;

    @Column(name = "activated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime activatedAt;
}
