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
     * Sorted names of every permission — the all-permissions set materialized for the
     * {@code all_permissions} flag short-circuit. Explicit JPQL: a derived query
     * would return entities, not the {@code name} column.
     */
    @Query("select p.name from Permission p order by p.name asc")
    List<String> findAllNames();

    /** Whether any live role still holds the permission — deletion is blocked until unassigned. */
    @Query("select case when count(r) > 0 then true else false end "
            + "from Role r join r.permissions p where p.id = :permissionId")
    boolean isInUse(@Param("permissionId") UUID permissionId);
}
