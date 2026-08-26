package com.ibrhalil.forgesys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Subscription plan (K-16) — reference data seeded from {@code PlanDefinition} at startup. */
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

    /** Plan ordering (FREE &lt; PRO &lt; ENTERPRISE); a module's {@code minPlan} rank gates activation. */
    @Column(name = "plan_rank", nullable = false)
    private int rank;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
