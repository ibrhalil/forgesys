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
 * A saved view of a custom {@link App} (K-15 / Epic 3.0.B): a {@link ViewType} plus a
 * <em>structured</em> {@code config} JSON ({@code filters/sorts/groupBy} referencing
 * property ids — validated against the app's property set; deliberately NOT a free-text
 * expression language, so the injection surface is structural, see ROADMAP 3.0.B spike).
 *
 * <p>NOTE on naming: this is a writable domain entity named after its K-15 table
 * {@code t_app_views} — not a read-model projection (the former
 * {@code @Immutable @Subselect} {@code UserDirectoryView} read model moved to an
 * in-code Criteria DTO projection, K-49).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "config")
@Table(
        name = "t_app_views",
        indexes = {
                @Index(name = "idx_app_views_app", columnList = "app_id")
        }
)
@SQLDelete(sql = "UPDATE t_app_views SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class AppView extends BaseEntity {

    @Column(name = "app_id", nullable = false, columnDefinition = "uuid")
    private UUID appId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "view_type", nullable = false, length = 20)
    private ViewType type;

    /** Structured filter/sort/grouping config (raw JSON string, {@code jsonb}). */
    @Column(columnDefinition = "jsonb")
    private String config;

    /** View order inside the app (tab order in the UI). */
    @Column(nullable = false)
    private int position = 0;
}
