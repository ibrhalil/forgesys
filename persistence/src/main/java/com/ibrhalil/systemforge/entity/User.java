package com.ibrhalil.systemforge.entity;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.SQLDelete;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "t_users", indexes = {
        @Index(name = "idx_user_username", columnList = "username"),
        @Index(name = "idx_user_email", columnList = "email")
})
@SQLDelete(sql = "UPDATE t_users SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
@EntityListeners(AuditingEntityListener.class)
@ToString(exclude = {"password", "groups", "roles"})
public class User extends BaseEntity {

    @Column(nullable = false, length = 70, unique = true)
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @OneToOne(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    private UserProfile userProfile;

    @OneToOne(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE}, optional = false)
    private UserAccount userAccount;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(length = 512)
    private String emailVerificationToken;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime emailVerificationTokenExpiresAt;

    @Column(length = 512)
    private String passwordResetToken;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime passwordResetTokenExpiresAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_user_roles",
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_roles_user_role",
                    columnNames = {"user_id", "role_id"}
            ),
            joinColumns = @JoinColumn(name = "user_id",
                    foreignKey = @ForeignKey(name = "fk_user_roles_user")),
            inverseJoinColumns = @JoinColumn(name = "role_id",
                    foreignKey = @ForeignKey(name = "fk_user_roles_role"))
    )
    private Set<Role> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_user_groups",
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_groups_user_group",
                    columnNames = {"user_id", "group_id"}
            ),
            joinColumns = @JoinColumn(name = "user_id",
                    foreignKey = @ForeignKey(name = "fk_user_groups_user")),
            inverseJoinColumns = @JoinColumn(name = "group_id",
                    foreignKey = @ForeignKey(name = "fk_user_groups_group"))
    )
    private Set<Group> groups = new HashSet<>();
}
