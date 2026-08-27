package com.ibrhalil.forgesys.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A tenant's custom app (K-15, Notion/Airtable-style): {@link CustomAppProperty} columns,
 * {@link CustomAppRecord} rows, {@link CustomAppView} saved renderings. Hosted in an APPS-type
 * project collection (K-45). Name uniqueness is a partial index in PG; the entity
 * {@code unique = true} only shapes H2 create-drop (same as {@link Project}).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_custom_apps",
        indexes = {
                @Index(name = "idx_custom_apps_name", columnList = "name"),
                @Index(name = "idx_custom_apps_project", columnList = "project_id")
        }
)
@SQLDelete(sql = "UPDATE t_custom_apps SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class CustomApp extends BaseEntity {

    @Column(nullable = false, length = 150, unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Free-form icon identifier (e.g. emoji or icon name); rendered by the UI. */
    @Column(length = 50)
    private String icon;

    /** Owning APPS-type collection container (K-45; NOT NULL + FK ON DELETE CASCADE in module/apps/V2). */
    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;
}
