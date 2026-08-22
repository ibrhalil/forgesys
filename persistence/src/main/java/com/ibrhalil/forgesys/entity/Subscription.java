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
 * A tenant's subscription to a {@link Plan} (K-16 / Epic 3.0.A). One subscription row per
 * company (partial unique {@code (company_id) WHERE is_deleted = false}); plan changes in
 * Faz 6 will UPDATE the row, not insert history (audit logs carry the trail).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"company", "plan"})
@Table(
        name = "t_subscriptions",
        schema = "public",
        indexes = {
                @Index(name = "idx_subscriptions_company", columnList = "company_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_subscriptions_company", columnNames = "company_id")
        }
)
@SQLDelete(sql = "UPDATE t_subscriptions SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class Subscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_subscriptions_company"))
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_subscriptions_plan"))
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime endedAt;
}
