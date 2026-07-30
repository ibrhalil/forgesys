package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * List lookup redeclared to attach an {@link EntityGraph} (roles + groups + profile
     * + account) so the paginated list query avoids N+1 when building
     * {@code UserResponse} ([RISK-27]). All list reads go through the
     * Specification-driven engine; the graph applies to plain and filtered lists alike.
     * {@code roles}/{@code groups} are {@code Set}s (not bags), so fetching both plus
     * the two {@code @OneToOne} associations in one graph is safe.
     */
    @Override
    @EntityGraph(attributePaths = {"roles", "groups", "userProfile", "userAccount"})
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    /**
     * Same {@link EntityGraph} as {@link #findAll(Pageable)} for the single-row lookup.
     * {@code UserService.findById}/{@code getUserOrThrow} resolve a {@code User} and then
     * read profile/account/roles/groups when building {@code UserResponse}; without this
     * override each of those accessors fires an extra SELECT on the tenant schema. As
     * with {@code findAll}, the {@code Set}-typed collections make fetching both plus
     * the two {@code @OneToOne} associations in one graph safe.
     */
    @Override
    @EntityGraph(attributePaths = {"roles", "groups", "userProfile", "userAccount"})
    Optional<User> findById(UUID id);

    @EntityGraph(attributePaths = {"groups"})
    @Query("select u from User u join u.groups g where g.id = :groupId")
    List<User> findGroupMembers(@Param("groupId") UUID groupId);

    /**
     * Effective-authority resolution (replaces nested lazy-collection traversal).
     * Direct role ids of a user ({@code t_user_roles}). Used by
     * {@code CustomUserDetailsService.resolveAuthorities(UUID)} to build the JWT
     * authority set without relying on {@code @EntityGraph} (the full graph
     * {@code groups.roles.permissions} + {@code roles.parentRoles} cannot be fetched in a
     * single graph — Hibernate multiple-bags limit) and without N+1 lazy loads.
     */
    @Query("select distinct r.id from User u join u.roles r where u.id = :userId")
    List<UUID> findDirectRoleIds(@Param("userId") UUID userId);

    /**
     * Users DIRECTLY holding {@code roleId} ({@code t_user_roles}), as entities. Used
     * by {@code RoleService.delete} to detach the role from every holder's collection
     * before the soft-delete — leaving the join rows in place keeps managed
     * {@code User.roles} collections referencing a deleted role, which fails the flush
     * with {@code TransientPropertyValueException}.
     */
    @Query("select distinct u from User u join u.roles r where r.id = :roleId")
    List<User> findUsersByRole(@Param("roleId") UUID roleId);

    /**
     * Role ids reachable through the user's <em>active</em> groups ({@code t_user_groups} +
     * {@code t_group_roles}). Inactive groups ({@code active = false}) are excluded so a
     * deactivated group drops its permissions immediately. Companion to
     * {@link #findDirectRoleIds(UUID)}.
     */
    @Query("select distinct gr.id from User u join u.groups g join g.roles gr "
            + "where u.id = :userId and g.active = true")
    List<UUID> findActiveGroupRoleIds(@Param("userId") UUID userId);

    /**
     * One hop of role inheritance ({@code t_role_parents}) — the direct parents of the
     * given roles. {@code CustomUserDetailsService} walks this iteratively (BFS with a
     * visited set) to resolve the transitive parent closure; {@code @SQLRestriction}
     * filters soft-deleted parents so orphan join rows are harmless.
     */
    @Query("select distinct p.id from Role r join r.parentRoles p where r.id in :roleIds")
    List<UUID> findParentRoleIds(@Param("roleIds") Collection<UUID> roleIds);

    /**
     * Distinct permission names ({@code {module}:{resource}:{action}}) granted by the
     * given role ids ({@code t_role_permissions}). The terminal step of authority
     * resolution — returns wire strings directly, no entity loading.
     */
    @Query("select distinct p.name from Role r join r.permissions p where r.id in :roleIds")
    List<String> findPermissionNamesByRoleIds(@Param("roleIds") Collection<UUID> roleIds);

    /**
     * Single-column projection of {@code UserAccount.tokenInvalidBefore} for the
     * {@code JwtAuthenticationFilter} revocation check ([RISK-21]). {@code UserAccount}
     * shares the user PK via {@code @MapsId}, so this hits one row/column without a
     * {@code JOIN} or a lazy {@code @OneToOne} proxy. Empty when the user or account
     * row is absent (deleted account / unknown subject).
     */
    @Query("select ua.tokenInvalidBefore from UserAccount ua where ua.id = :userId")
    Optional<OffsetDateTime> findTokenInvalidBefore(@Param("userId") UUID userId);

    /**
     * Ids of every user holding {@code roleId}, directly ({@code t_user_roles}) or via an
     * <em>active</em> group ({@code t_user_groups} + {@code t_group_roles}). Used by
     * {@code SessionRevocationService} to drop sessions of everyone affected by a
     * role/permission mutation (Faz 1 — privilege changes take effect immediately, not at
     * the next access-token TTL).
     */
    @Query("""
            select distinct u.id from User u
            where u.id in (select u2.id from User u2 join u2.roles r where r.id = :roleId)
               or u.id in (select u2.id from User u2 join u2.groups g join g.roles gr
                           where g.active = true and gr.id = :roleId)
            """)
    List<UUID> findUserIdsByRole(@Param("roleId") UUID roleId);

    /**
     * Ids of every member of a group ({@code t_user_groups}). Lean projection of
     * {@link #findGroupMembers(UUID)} for {@code SessionRevocationService} (ids only,
     * no entity loading).
     */
    @Query("select u.id from User u join u.groups g where g.id = :groupId")
    List<UUID> findUserIdsByGroup(@Param("groupId") UUID groupId);

    /**
     * Ids of the caller's own groups ({@code t_user_groups}) — resolves the
     * {@code iam:group-member:read} visibility scope (the caller sees members of these
     * groups plus themselves).
     */
    @Query("select distinct g.id from User u join u.groups g where u.id = :userId")
    List<UUID> findGroupIdsByUserId(@Param("userId") UUID userId);

    /**
     * Ids of every member of any of the given groups ({@code t_user_groups}) — the
     * visible-user set for the {@code iam:group-member:read} scope. Companion to
     * {@link #findGroupIdsByUserId(UUID)}; the caller's own id is appended by the
     * caller (a user with no groups still sees themselves).
     */
    @Query("select distinct u.id from User u join u.groups g where g.id in :groupIds")
    List<UUID> findUserIdsByGroupIds(@Param("groupIds") Collection<UUID> groupIds);

    /**
     * Whether at least one <em>enabled</em>, non-soft-deleted user holds any of the given
     * roles — directly ({@code t_user_roles}) or via an <em>active</em> group
     * ({@code t_user_groups} + {@code t_group_roles}). The single-existence form of
     * {@link #findUserIdsByRole(UUID)} for {@code LastAdminGuard}'s "at least one active
     * admin remains" invariant ({@code @SQLRestriction} hides soft-deleted users;
     * disabled accounts don't count as admins).
     */
    @Query("""
            select count(u.id) > 0 from User u
            join u.userAccount a
            where a.enabled = true
              and (u.id in (select u2.id from User u2 join u2.roles r where r.id in :roleIds)
                or u.id in (select u2.id from User u2 join u2.groups g join g.roles gr
                            where g.active = true and gr.id in :roleIds))
            """)
    boolean existsEnabledByRoleIds(@Param("roleIds") Collection<UUID> roleIds);

    /**
     * Bulk-stamps {@code tokenInvalidBefore = now} for the given users so every
     * outstanding access token whose {@code iat} predates {@code now} is rejected by
     * {@code JwtAuthenticationFilter} (user-scoped revoke, [RISK-21]). {@code flushAutomatically}
     * flushes pending entity changes (the role/group/password mutation that triggered the
     * revoke) before the UPDATE so nothing is lost; {@code UserAccount} shares the user PK
     * ({@code @MapsId}), hence {@code a.id in :userIds}. Returns the number of rows updated.
     */
    @Modifying(flushAutomatically = true)
    @Query("update UserAccount a set a.tokenInvalidBefore = :now where a.id in :userIds")
    int bulkSetTokenInvalidBefore(@Param("userIds") Collection<UUID> userIds, @Param("now") OffsetDateTime now);
}
