package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    @EntityGraph(attributePaths = {"roles"})
    List<User> findByRolesEmpty();

    /**
     * {@inheritDoc}
     * Redeclared to attach an {@link EntityGraph} (roles + groups + profile + account)
     * so the paginated list query avoids N+1 when building {@code UserResponse}
     * ([RISK-27]). {@code roles}/{@code groups} are {@code Set}s (not bags), so fetching
     * both plus the two {@code @OneToOne} associations in one graph is safe.
     */
    @Override
    @EntityGraph(attributePaths = {"roles", "groups", "userProfile", "userAccount"})
    Page<User> findAll(Pageable pageable);

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
