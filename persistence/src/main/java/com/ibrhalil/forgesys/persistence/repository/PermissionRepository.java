package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID>, JpaSpecificationExecutor<Permission> {

    boolean existsByName(String name);

    Optional<Permission> findByName(String name);

    List<Permission> findAllByNameIn(Collection<String> names);

    /**
     * Just the names of every permission in the tenant, ordered — the all-permissions
     * set materialized for roles carrying the {@code all_permissions} flag (resolution
     * short-circuit). Explicit JPQL projection (a derived {@code find...By} would return
     * entities, not the {@code name} column).
     */
    @Query("select p.name from Permission p order by p.name asc")
    List<String> findAllNames();

    /**
     * Whether any (non-soft-deleted) role still holds this permission via
     * {@code t_role_permissions}. {@code PermissionService.delete} blocks deletion while
     * this is true — a permission in use by a role is a live part of the RBAC graph and
     * must be unassigned before it can be removed. {@code @SQLRestriction} filters
     * soft-deleted roles/permissions from the join.
     */
    @Query("select case when count(r) > 0 then true else false end "
            + "from Role r join r.permissions p where p.id = :permissionId")
    boolean isInUse(@Param("permissionId") UUID permissionId);
}
