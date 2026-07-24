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
     * Redeclared to attach an {@link EntityGraph} (roles + groups + profile + account)
     * so the paginated list query avoids N+1 when building {@code UserResponse}
     * ([RISK-27]). {@code roles}/{@code groups} are {@code Set}s (not bags), so fetching
     * both plus the two {@code @OneToOne} associations in one graph is safe.
     */
    @Override
    @EntityGraph(attributePaths = {"roles", "groups", "userProfile", "userAccount"})
    Page<User> findAll(Pageable pageable);

    @Query("select u from User u join u.groups g where g.id = :groupId")
    List<User> findGroupMembers(@Param("groupId") UUID groupId);
}
