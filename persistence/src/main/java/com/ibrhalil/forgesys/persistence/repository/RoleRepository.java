package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID>, JpaSpecificationExecutor<Role> {

    boolean existsByName(String name);

    @EntityGraph(attributePaths = {"permissions", "parentRoles"})
    Optional<Role> findByName(String name);

    /**
     * Whether any role among the given ids carries the {@code all_permissions} flag.
     * Used by {@code CustomUserDetailsService.resolvePermissionNames} (after the
     * parent-role closure) to short-circuit to the full permission set. A role that
     * transitively inherits from an all-permissions role is itself all-permissions.
     */
    boolean existsByIdInAndAllPermissionsTrue(Collection<UUID> ids);

    /**
     * Every role carrying the {@code all_permissions} flag. Used by
     * {@code SessionRevocationService.revokeAllPermissionsRoleHolders} to refresh the
     * outstanding tokens of all-permissions-role bearers when a permission is created or
     * renamed (so the new/renamed name reaches them on their next request).
     */
    List<Role> findAllByAllPermissionsTrue();

    /**
     * One hop of role inheritance <em>towards the children</em> ({@code t_role_parents}):
     * the roles that inherit from (have as a parent) any of the given roles. Used by
     * {@code LastAdminGuard} to expand the admin-capable role set downward — a role
     * inheriting from an all-permissions role is itself admin-capable. Traversed
     * iteratively (BFS with a visited set) to a fixpoint; {@code @SQLRestriction}
     * filters soft-deleted children.
     */
    @Query("select distinct r.id from Role r join r.parentRoles p where p.id in :parentIds")
    List<UUID> findChildRoleIds(@Param("parentIds") Collection<UUID> parentIds);

    /**
     * List lookup redeclared to attach an {@link EntityGraph} (permissions + direct
     * parents) so the paginated list query avoids N+1 when building
     * {@code RoleResponse} (which exposes parents) — Faz 4a inheritance. Serves both
     * plain and Specification-driven (filter engine) list reads.
     */
    @Override
    @EntityGraph(attributePaths = {"permissions", "parentRoles"})
    Page<Role> findAll(Specification<Role> spec, Pageable pageable);

    /**
     * Same {@link EntityGraph} as {@link #findAll(Pageable)} for the single-row lookup.
     * {@code RoleService.findById}/{@code getRoleOrThrow} resolve a {@code Role} and then
     * read {@code permissions}/{@code parentRoles} when building {@code RoleResponse};
     * without this override each accessor fires an extra SELECT on the tenant schema.
     */
    @Override
    @EntityGraph(attributePaths = {"permissions", "parentRoles"})
    Optional<Role> findById(UUID id);
}
