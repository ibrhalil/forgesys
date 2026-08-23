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
 * A note category (shared taxonomy for {@link Note}s, K-44 / Epic 3.2). Soft-deletable,
 * optimistic-locked, tenant-audited. Name uniqueness is a partial index
 * ({@code WHERE is_deleted = false}) in {@code module/notes/V1}; the entity-side
 * {@code unique = true} only shapes H2 create-drop in tests ([RISK-17]).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_note_categories",
        indexes = {
                @Index(name = "idx_note_categories_name", columnList = "name")
        }
)
@SQLDelete(sql = "UPDATE t_note_categories SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class NoteCategory extends BaseEntity {

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    /** Optional UI color token (e.g. {@code #aabbcc} or a Tailwind class fragment). */
    @Column(length = 20)
    private String color;
}
