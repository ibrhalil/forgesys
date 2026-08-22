package com.ibrhalil.forgesys.entity;

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
 * A tenant's custom app (K-15 / Epic 3.0.B, Notion/Airtable-style): a user-defined
 * "table" made of {@link AppProperty definitions} (columns), {@link AppRecord records}
 * (rows) and {@link AppView views} (saved renderings). Ships with the {@code apps}
 * module ({@code db/migration/module/apps}, ownMigrations — activated per tenant).
 *
 * <p>Soft-deletable, optimistic-locked, tenant-audited — same base as the other tenant
 * product entities. Name uniqueness is a partial index ({@code WHERE is_deleted =
 * false}); the entity-side {@code unique = true} only shapes H2 create-drop in tests
 * (same as {@link Project}).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_apps",
        indexes = {
                @Index(name = "idx_apps_name", columnList = "name")
        }
)
@SQLDelete(sql = "UPDATE t_apps SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class App extends BaseEntity {

    @Column(nullable = false, length = 150, unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Free-form icon identifier (e.g. emoji or icon name); rendered by the UI. */
    @Column(length = 50)
    private String icon;
}
