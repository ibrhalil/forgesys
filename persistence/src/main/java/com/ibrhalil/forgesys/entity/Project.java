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
 * Tenant-scoped typed project container (K-45). A project cannot exist without a
 * {@link ProjectType} — the type decides which built-in module's content lives inside
 * (TASKS: board, NOTES: notes, APPS: custom app collection). {@code parentProjectId}
 * adds optional single-level-style nesting (the depth is a user choice, the app only
 * ever navigates one level); {@code isDefault} marks the per-type "Genel" container
 * ensured by module activation (type and parent frozen on such rows).
 *
 * <p>Soft-deletable, optimistic-locked, tenant-audited — same base as Role/Group.
 * Name uniqueness is PER-TYPE ({@code uk_projects_type_name ON (project_type, name)
 * WHERE is_deleted = false} in {@code tenant/V3}) — each type is its own namespace,
 * so the notes/apps default containers may share the "Genel" name. The entity carries
 * no {@code unique} (a partial composite index cannot shape H2 create-drop — the
 * App/NoteCategory convention; the service enforces per-type checks under H2).
 * {@code tenant/V3} adds the parent self-FK and the per-type default marker too.
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
