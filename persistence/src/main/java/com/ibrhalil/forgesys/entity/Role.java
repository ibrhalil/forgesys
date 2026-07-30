package com.ibrhalil.forgesys.entity;

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
@NoArgsConstructor
@ToString(exclude = {"permissions", "parentRoles"})
@Table(
        name = "t_roles",
        indexes = {
                @Index(name = "idx_role_name", columnList = "name")
        }
)
@SQLDelete(sql = "UPDATE t_roles SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class Role extends BaseEntity {

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_role_permissions",
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_role_permissions_role_permission",
                    columnNames = {"role_id", "permission_id"}
            ),
            joinColumns = @JoinColumn(name = "role_id",
                    foreignKey = @ForeignKey(name = "fk_role_permissions_role")),
            inverseJoinColumns = @JoinColumn(name = "permission_id",
                    foreignKey = @ForeignKey(name = "fk_role_permissions_permission"))
    )
    private Set<Permission> permissions = new HashSet<>();

    /**
     * Faz 4a role inheritance: the roles whose permissions this role <em>inherits</em>.
     * A role's effective permission set is its own {@link #permissions} plus the
     * (transitive) permissions of every {@code parentRoles} entry. Resolved recursively
     * by {@code CustomUserDetailsService.resolveAuthorities} with a visited-set guard, so
     * even a malformed cycle can't infinite-loop. {@code RoleService.setParents} enforces
     * acyclicity (no self-parent, no path back to the child) on assignment.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_role_parents",
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_role_parents_role_parent",
                    columnNames = {"role_id", "parent_role_id"}
            ),
            joinColumns = @JoinColumn(name = "role_id",
                    foreignKey = @ForeignKey(name = "fk_role_parents_role")),
            inverseJoinColumns = @JoinColumn(name = "parent_role_id",
                    foreignKey = @ForeignKey(name = "fk_role_parents_parent"))
    )
    private Set<Role> parentRoles = new HashSet<>();
}
