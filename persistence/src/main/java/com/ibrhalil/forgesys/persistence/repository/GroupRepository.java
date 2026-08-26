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
     * Groups carrying {@code roleId} — detached from the role before its soft-delete;
     * stale join rows in managed collections fail the flush
     * ({@code TransientPropertyValueException}).
     */
    @Query("select g from Group g join g.roles r where r.id = :roleId")
    List<Group> findGroupsByRole(@Param("roleId") UUID roleId);

    /** List with roles attached — N+1-free for plain and Specification reads. */
    @Override
    @EntityGraph(attributePaths = "roles")
    Page<Group> findAll(Specification<Group> spec, Pageable pageable);

    /** Single-row lookup with the same graph as {@link #findAll(Specification, Pageable)}. */
    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<Group> findById(UUID id);

    @Query(value = "select count(*) from t_user_groups where group_id = :groupId", nativeQuery = true)
    long countMembers(@Param("groupId") UUID groupId);
}
