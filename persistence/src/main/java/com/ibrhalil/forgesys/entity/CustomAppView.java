package com.ibrhalil.forgesys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

/**
 * A saved view of a custom {@link CustomApp} (K-15): {@link ViewType} + <em>structured</em>
 * {@code config} JSON ({@code filters/sorts/groupBy} on property ids — deliberately
 * not a free-text expression language). A writable domain entity, not a read-model
 * projection (K-49).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "config")
@Table(
        name = "t_custom_app_views",
        indexes = {
                @Index(name = "idx_custom_app_views_custom_app", columnList = "custom_app_id")
        }
)
@SQLDelete(sql = "UPDATE t_custom_app_views SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class CustomAppView extends BaseEntity {

    @Column(name = "custom_app_id", nullable = false, columnDefinition = "uuid")
    private UUID customAppId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "view_type", nullable = false, length = 20)
    private ViewType type;

    /** Structured filter/sort/grouping config (raw JSON string, {@code jsonb}). */
    @Column(columnDefinition = "jsonb")
    private String config;

    /** View order inside the custom app (tab order in the UI). */
    @Column(nullable = false)
    private int position = 0;
}
