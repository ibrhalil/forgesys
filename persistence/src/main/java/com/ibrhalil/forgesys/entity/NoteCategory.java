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
 * Note category inside a NOTES-type container (K-44/K-45). Name uniqueness is a
 * partial index in {@code module/notes/V1}; the entity {@code unique = true} only
 * shapes H2 create-drop (RISK-17). Project is fixed at create — categories never move.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_note_categories",
        indexes = {
                @Index(name = "idx_note_categories_name", columnList = "name"),
                @Index(name = "idx_note_categories_project", columnList = "project_id")
        }
)
@SQLDelete(sql = "UPDATE t_note_categories SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class NoteCategory extends BaseEntity {

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    /** Optional UI color token (e.g. {@code #aabbcc} or a Tailwind class fragment). */
    @Column(length = 20)
    private String color;

    /** Owning NOTES-type container (K-45; NOT NULL + FK ON DELETE CASCADE in module/notes/V2). */
    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;
}
