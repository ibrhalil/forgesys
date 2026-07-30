package com.ibrhalil.forgesys.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

/**
 * Read-only, flattened user directory for the list/search endpoints. The joins
 * (profile + account) and the role/group counts run in the database as a derived
 * table — replacing the former {@code @EntityGraph} fetch (whose row multiplication
 * the {@code Set} dedup then undid in memory) with a single flat scan. No
 * associations means N+1 is structurally impossible here.
 *
 * <p>Mapped with {@link Subselect} rather than a physical view: the SQL below runs on
 * both PostgreSQL (Flyway-owned tenant schemas) and the H2 test profile (create-drop —
 * subselect entities generate no schema), with zero migrations. A real
 * {@code CREATE VIEW} can supersede this later for BI/DBA needs without touching the
 * mapping (same column names).
 *
 * <p>Soft-delete semantics: {@code t_users.is_deleted} filters the rows, and the
 * profile/account joins carry their own {@code is_deleted = false} conditions so a
 * soft-deleted profile does not blank the name columns. The join-table counts have no
 * soft-delete flag by design (join rows are hard-managed).
 */
@Entity
@Immutable
@Subselect("""
        select u.id as id,
               u.username as username,
               u.email as email,
               u.email_verified as email_verified,
               p.first_name as first_name,
               p.last_name as last_name,
               a.enabled as enabled,
               a.locked_until as locked_until,
               a.last_login_at as last_login_at,
               u.created_at as created_at,
               (select count(*) from t_user_roles ur where ur.user_id = u.id) as role_count,
               (select count(*) from t_user_groups ug where ug.user_id = u.id) as group_count
        from t_users u
        left join t_user_profiles p on p.user_id = u.id and p.is_deleted = false
        left join t_user_accounts a on a.user_id = u.id and a.is_deleted = false
        where u.is_deleted = false
        """)
@Synchronize({"t_users", "t_user_profiles", "t_user_accounts", "t_user_roles", "t_user_groups"})
@Getter
public class UserDirectoryView {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "username")
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "email_verified")
    private boolean emailVerified;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "enabled")
    private boolean enabled;

    /**
     * [RISK-22] Brute-force lockout expiry from the account row. Non-null does NOT
     * mean "currently locked" — expiry is lazy (cleared on the next login attempt
     * or admin unlock); consumers must compare against {@code now}.
     */
    @Column(name = "locked_until", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdDate;

    @Column(name = "role_count")
    private long roleCount;

    @Column(name = "group_count")
    private long groupCount;
}
