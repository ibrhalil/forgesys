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
 * Tenant-scoped typed project container (K-45): the {@link ProjectType} decides which
 * module's content lives inside. Name uniqueness is PER-TYPE (partial index
 * {@code uk_projects_type_name} in {@code tenant/V3}); the entity carries no
 * {@code unique} — a partial composite index cannot shape H2 create-drop.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_projects",
        indexes = {
                @Index(name = "idx_project_name", columnList = "name"),
                @Index(name = "idx_projects_parent", columnList = "parent_project_id")
        }
)
@SQLDelete(sql = "UPDATE t_projects SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class Project extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 30)
    private ProjectType type;

    /** Plain UUID column (Task convention — no {@code @ManyToOne} proxy); cycle guarded in the service. */
    @Column(name = "parent_project_id", columnDefinition = "uuid")
    private UUID parentProjectId;

    /** Per-type default container marker; system-managed (not settable through the API). */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
