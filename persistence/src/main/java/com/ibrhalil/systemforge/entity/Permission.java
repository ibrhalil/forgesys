package com.ibrhalil.systemforge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_permissions",
        indexes = {
                @Index(name = "idx_permission_name", columnList = "name")
        }
)
@SQLDelete(sql = "UPDATE t_permissions SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class Permission extends BaseEntity {

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(length = 500)
    private String description;
}
