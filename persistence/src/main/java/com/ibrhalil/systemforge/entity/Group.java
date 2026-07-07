package com.ibrhalil.systemforge.entity;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.SQLDelete;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@Table(
        name = "t_groups",
        indexes = {
                @Index(name = "idx_group_name", columnList = "name")
        }
)
@NoArgsConstructor
@ToString(exclude = "roles")
@SQLDelete(sql = "UPDATE t_groups SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class Group extends BaseEntity {

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_group_roles",
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_group_roles_group_role",
                    columnNames = {"group_id", "role_id"}
            ),
            joinColumns = @JoinColumn(name = "group_id",
                    foreignKey = @ForeignKey(name = "fk_group_roles_group")),
            inverseJoinColumns = @JoinColumn(name = "role_id",
                    foreignKey = @ForeignKey(name = "fk_group_roles_role"))
    )
    private Set<Role> roles = new HashSet<>();
}
