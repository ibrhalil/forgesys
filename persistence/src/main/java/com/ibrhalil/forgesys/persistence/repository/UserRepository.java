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

/**
 * Tenant-schema user repository. Authority-resolution chain, session-revoke
 * projections and the visibility-scope queries live here — rationale and the
 * RISK-21/27 background: docs/CODE_NOTES.md (persistence → UserRepository).
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User>, UserRepositoryCustom {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /** Paginated list with the roles/groups/profile/account graph attached (N+1-free list reads). */
    @Override
    @EntityGraph(attributePaths = {"roles", "groups", "userProfile", "userAccount"})
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    /** Single-row lookup with the same graph as {@link #findAll(Specification, Pageable)}. */
    @Override
    @EntityGraph(attributePaths = {"roles", "groups", "userProfile", "userAccount"})
    Optional<User> findById(UUID id);

    /** Group members as entities (group detail page). */
    @EntityGraph(attributePaths = {"groups"})
    @Query("select u from User u join u.groups g where g.id = :groupId")
    List<User> findGroupMembers(@Param("groupId") UUID groupId);

    /** Direct role ids ({@code t_user_roles}) — authority resolution step 1. */
    @Query("select distinct r.id from User u join u.roles r where u.id = :userId")
    List<UUID> findDirectRoleIds(@Param("userId") UUID userId);

    /** Users DIRECTLY holding {@code roleId}, as entities — detach before role soft-delete. */
    @Query("select distinct u from User u join u.roles r where r.id = :roleId")
    List<User> findUsersByRole(@Param("roleId") UUID roleId);

    /** Role ids reachable through the user's ACTIVE groups (inactive groups drop out). */
    @Query("select distinct gr.id from User u join u.groups g join g.roles gr "
            + "where u.id = :userId and g.active = true")
    List<UUID> findActiveGroupRoleIds(@Param("userId") UUID userId);

    /** One hop of role inheritance; soft-deleted parents filtered by the soft-delete filter. */
    @Query("select distinct p.id from Role r join r.parentRoles p where r.id in :roleIds")
    List<UUID> findParentRoleIds(@Param("roleIds") Collection<UUID> roleIds);

    /** Distinct permission names granted by the given role ids — authority resolution terminal step. */
    @Query("select distinct p.name from Role r join r.permissions p where r.id in :roleIds")
    List<String> findPermissionNamesByRoleIds(@Param("roleIds") Collection<UUID> roleIds);

    /** Single-column {@code tokenInvalidBefore} projection for the JWT revocation check (RISK-21). */
    @Query("select ua.tokenInvalidBefore from UserAccount ua where ua.id = :userId")
    Optional<OffsetDateTime> findTokenInvalidBefore(@Param("userId") UUID userId);

    /** Ids of every user holding {@code roleId} directly or via an active group — session revoke. */
    @Query("""
            select distinct u.id from User u
            where u.id in (select u2.id from User u2 join u2.roles r where r.id = :roleId)
               or u.id in (select u2.id from User u2 join u2.groups g join g.roles gr
                           where g.active = true and gr.id = :roleId)
            """)
    List<UUID> findUserIdsByRole(@Param("roleId") UUID roleId);

    /** Member ids of a group — session revoke (lean companion of {@link #findGroupMembers(UUID)}). */
    @Query("select u.id from User u join u.groups g where g.id = :groupId")
    List<UUID> findUserIdsByGroup(@Param("groupId") UUID groupId);

    /** Caller's own group ids — {@code iam:group-member:read} visibility scope. */
    @Query("select distinct g.id from User u join u.groups g where u.id = :userId")
    List<UUID> findGroupIdsByUserId(@Param("userId") UUID userId);

    /** Members of the given groups — the visible-user set for the scope (caller appends self). */
    @Query("select distinct u.id from User u join u.groups g where g.id in :groupIds")
    List<UUID> findUserIdsByGroupIds(@Param("groupIds") Collection<UUID> groupIds);

    /** Whether at least one enabled user holds any of the roles — the LastAdminGuard invariant. */
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
     * Earliest-created enabled holder of any of the roles (K-50 impersonation target);
     * sort/{@code limit 1} come from the {@code Pageable} (caller passes
     * {@code createdDate ASC, id ASC}). Same role paths as {@link #existsEnabledByRoleIds}.
     */
    @Query("""
            select u from User u
            join u.userAccount a
            where a.enabled = true
              and (u.id in (select u2.id from User u2 join u2.roles r where r.id in :roleIds)
                or u.id in (select u2.id from User u2 join u2.groups g join g.roles gr
                            where g.active = true and gr.id in :roleIds))
            """)
    List<User> findFirstEnabledByRoleIds(@Param("roleIds") Collection<UUID> roleIds, Pageable pageable);

    /**
     * Bulk-stamps {@code tokenInvalidBefore = now} for the given users (user-scoped
     * revoke, RISK-21). Returns the number of rows updated.
     */
    @Modifying(flushAutomatically = true)
    @Query("update UserAccount a set a.tokenInvalidBefore = :now where a.id in :userIds")
    int bulkSetTokenInvalidBefore(@Param("userIds") Collection<UUID> userIds, @Param("now") OffsetDateTime now);
}
