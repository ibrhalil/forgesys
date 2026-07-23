package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Redeclared to attach an {@link EntityGraph} (roles + groups) so the paginated
     * list query avoids N+1 when building {@code UserResponse}.
     */
    @Override
    @EntityGraph(attributePaths = {"roles", "groups"})
    Page<User> findAll(Pageable pageable);

    @Query("select u from User u join u.groups g where g.id = :groupId")
    List<User> findGroupMembers(@Param("groupId") UUID groupId);
}
