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
 * A column definition of a custom {@link App} (K-15): name + {@link PropertyType} +
 * type-dependent {@code config} JSON (SELECT {@code options}, RELATION
 * {@code targetAppId}) validated in the service. {@code appId} is a plain UUID
 * (Task convention).
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
