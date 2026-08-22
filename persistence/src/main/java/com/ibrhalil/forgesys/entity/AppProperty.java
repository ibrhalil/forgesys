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
 * A column definition of a custom {@link App} (K-15 / Epic 3.0.B): name + type + a
 * {@code config} JSON whose shape depends on {@link PropertyType} — SELECT carries
 * {@code {"options":[...]}}; RELATION carries {@code {"targetAppId":"<uuid>"}}; others
 * carry no config. Config is validated in the backend service layer per type.
 *
 * <p>{@code app} is a plain {@code UUID} column (not {@code @ManyToOne}) — same
 * lazy-proxy/N+1 avoidance rationale as {@link Task#getProjectId()}; validity is
 * enforced by the service (app existence) and by FK in the module migration.
 * Soft-delete: deleting a property hard-deletes its orphaned value rows
 * (service-level; {@code t_app_record_values} has no soft-delete).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "config")
@Table(
        name = "t_app_properties",
        indexes = {
                @Index(name = "idx_app_properties_app", columnList = "app_id")
        }
)
@SQLDelete(sql = "UPDATE t_app_properties SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class AppProperty extends BaseEntity {

    @Column(name = "app_id", nullable = false, columnDefinition = "uuid")
    private UUID appId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "prop_type", nullable = false, length = 20)
    private PropertyType type;

    /** Type-dependent JSON config (raw JSON string, mapped to {@code jsonb}). */
    @Column(columnDefinition = "jsonb")
    private String config;

    @Column(nullable = false)
    private boolean required = false;

    /** Column order inside the app (stable ordering for renderers). */
    @Column(nullable = false)
    private int position = 0;
}
