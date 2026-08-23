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

import java.util.UUID;

/**
 * A tenant-shared note anchored to its NOTES-type project container (K-44, re-scoped
 * by K-45). Content is markdown text — rendered client-side with raw HTML disabled
 * (no server-side sanitizing burden; the client never injects HTML). The category is
 * a plain {@code UUID} column (not {@code @ManyToOne}) per the Task convention (no
 * lazy proxies / N+1); validity is enforced in the service and by the FK in
 * {@code module/notes/V1} ({@code ON DELETE SET NULL} — deleting a category keeps
 * its notes, they become uncategorized; the category must belong to the same project
 * as the note, enforced in the service).
 *
 * <p>Soft-deletable, optimistic-locked, tenant-audited. No uniqueness constraint
 * (note titles may repeat).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_notes",
        indexes = {
                @Index(name = "idx_notes_category", columnList = "category_id"),
                @Index(name = "idx_notes_title", columnList = "title"),
                @Index(name = "idx_notes_project", columnList = "project_id")
        }
)
@SQLDelete(sql = "UPDATE t_notes SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class Note extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    /** Markdown text (may be empty — a note can be title-only). */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "category_id", columnDefinition = "uuid")
    private UUID categoryId;

    /** Owning NOTES-type container (K-45; NOT NULL + FK ON DELETE CASCADE in module/notes/V2). */
    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    /** Pinned notes sort to the top in the UI (client-side concern, no server ordering). */
    @Column(nullable = false)
    private boolean pinned;
}
