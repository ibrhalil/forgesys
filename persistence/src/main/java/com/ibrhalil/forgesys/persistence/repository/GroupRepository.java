package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID>, JpaSpecificationExecutor<Group> {

    boolean existsByName(String name);

    Optional<Group> findByName(String name);

    /**
     * Groups carrying {@code roleId} ({@code t_group_roles}). Used by
     * {@code RoleService.delete} to detach the role from every carrier's collection
     * before the soft-delete — leaving the join rows in place keeps managed
     * {@code Group.roles} collections referencing a deleted role, which fails the
     * flush with {@code TransientPropertyValueException}.
     */
    @Query("select g from Group g join g.roles r where r.id = :roleId")
    List<Group> findGroupsByRole(@Param("roleId") UUID roleId);

    /**
     * List lookup redeclared to attach an {@link EntityGraph} (roles) so the paginated
     * list query avoids N+1 when building {@code GroupResponse}. Serves both plain and
     * Specification-driven (filter engine) list reads.
     */
    @Override
    @EntityGraph(attributePaths = "roles")
    Page<Group> findAll(Specification<Group> spec, Pageable pageable);

    /**
     * Same {@link EntityGraph} as {@link #findAll(Pageable)} for the single-row lookup.
     * {@code GroupService.findById}/{@code getGroupOrThrow} resolve a {@code Group} and
     * then read {@code roles} when building {@code GroupResponse}; without this override
     * that accessor fires an extra SELECT on the tenant schema.
     */
    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<Group> findById(UUID id);

    @Query(value = "select count(*) from t_user_groups where group_id = :groupId", nativeQuery = true)
    long countMembers(@Param("groupId") UUID groupId);
}
