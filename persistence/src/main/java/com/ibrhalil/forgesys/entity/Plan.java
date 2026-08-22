package com.ibrhalil.forgesys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Subscription plan (K-16 / Epic 3.0.A). Reference data seeded from the code-side
 * {@code PlanDefinition} registry at startup ({@code PlanSyncRunner}) — soft-delete-less
 * ({@link GeneratedIdAuditEntity}).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_plans",
        schema = "public",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_plans_key", columnNames = "plan_key")
        }
)
public class Plan extends GeneratedIdAuditEntity {

    @Column(name = "plan_key", nullable = false, length = 50)
    private String key;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Ordering of plans (FREE &lt; PRO &lt; ENTERPRISE). A module's {@code minPlan} rank
     * gates activation: tenant plan rank must be &gt;= module min rank.
     */
    @Column(name = "plan_rank", nullable = false)
    private int rank;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
