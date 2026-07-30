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

/**
 * Tenant-scoped project (workspace container). A project's {@link ProjectType} decides
 * which built-in modules are surfaced inside it (the first product feature layer above
 * IAM). Tasks (and future modules) belong to a project.
 *
 * <p>Soft-deletable, optimistic-locked, tenant-audited — same base as Role/Group. The
 * name uniqueness is a partial index ({@code WHERE is_deleted = false}) in
 * {@code tenant/V4}; the entity-side {@code unique = true} only shapes H2 create-drop
 * in tests (Flyway owns the real schema, [RISK-17]).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_projects",
        indexes = {
                @Index(name = "idx_project_name", columnList = "name")
        }
)
@SQLDelete(sql = "UPDATE t_projects SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class Project extends BaseEntity {

    @Column(nullable = false, length = 150, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 30)
    private ProjectType type;
}
