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
     * Whether any of the roles carries the {@code all_permissions} flag — checked after
     * the parent closure (inheriting from an all-permissions role is itself all-permissions).
     */
    boolean existsByIdInAndAllPermissionsTrue(Collection<UUID> ids);

    /** Every all-permissions role — token-refresh targets when a permission is created/renamed. */
    List<Role> findAllByAllPermissionsTrue();

    /**
     * One hop of inheritance <em>towards the children</em> — {@code LastAdminGuard}
     * expands the admin-capable set downward with this (BFS to a fixpoint).
     */
    @Query("select distinct r.id from Role r join r.parentRoles p where p.id in :parentIds")
    List<UUID> findChildRoleIds(@Param("parentIds") Collection<UUID> parentIds);

    /** List with permissions + parents attached — N+1-free for plain and Specification reads. */
    @Override
    @EntityGraph(attributePaths = {"permissions", "parentRoles"})
    Page<Role> findAll(Specification<Role> spec, Pageable pageable);

    /** Single-row lookup with the same graph as {@link #findAll(Specification, Pageable)}. */
    @Override
    @EntityGraph(attributePaths = {"permissions", "parentRoles"})
    Optional<Role> findById(UUID id);
}
