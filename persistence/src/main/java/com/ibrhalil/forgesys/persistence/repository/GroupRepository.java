package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {

    boolean existsByName(String name);

    Optional<Group> findByName(String name);

    /**
     * {@inheritDoc}
     * Redeclared to attach an {@link EntityGraph} (roles) so the paginated list query
     * avoids N+1 when building {@code GroupResponse}.
     */
    @Override
    @EntityGraph(attributePaths = "roles")
    Page<Group> findAll(Pageable pageable);

    @Query(value = "select count(*) from t_user_groups where group_id = :groupId", nativeQuery = true)
    long countMembers(@Param("groupId") UUID groupId);
}
