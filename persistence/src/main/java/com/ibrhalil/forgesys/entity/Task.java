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

import java.time.LocalDate;
import java.util.UUID;

/**
 * A task belonging to a {@link Project} (the TASKS project type module, Faz 3 Stage 2).
 * Project and assignee are plain {@code UUID} columns (not {@code @ManyToOne}) to avoid
 * lazy-proxy/N+1 issues and to keep task queries cheap; validity is enforced in the
 * service (project/assignee existence) and by FK constraints in the migration.
 *
 * <p>Soft-deletable, optimistic-locked, tenant-audited — same base as the other tenant
 * entities. No uniqueness constraint (task titles may repeat).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_tasks",
        indexes = {
                @Index(name = "idx_tasks_project", columnList = "project_id"),
                @Index(name = "idx_tasks_assignee", columnList = "assignee_id")
        }
)
@SQLDelete(sql = "UPDATE t_tasks SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class Task extends BaseEntity {

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    @Column(name = "assignee_id", columnDefinition = "uuid")
    private UUID assigneeId;

    @Column(name = "due_date")
    private LocalDate dueDate;
}
