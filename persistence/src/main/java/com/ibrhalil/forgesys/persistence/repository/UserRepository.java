package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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

    /**
     * Same {@link EntityGraph} as {@link #findAll(Pageable)} for the single-row lookup.
     * {@code UserService.findById}/{@code getUserOrThrow} resolve a {@code User} and then
     * read profile/account/roles/groups when building {@code UserResponse}; without this
     * override each of those accessors fires an extra SELECT on the tenant schema. As
     * with {@code findAll}, the {@code Set}-typed collections make fetching both plus
     * the two {@code @OneToOne} associations in one graph safe.
     */
    @Override
    @EntityGraph(attributePaths = {"roles", "groups", "userProfile", "userAccount"})
    Optional<User> findById(UUID id);

    @EntityGraph(attributePaths = {"groups"})
    @Query("select u from User u join u.groups g where g.id = :groupId")
    List<User> findGroupMembers(@Param("groupId") UUID groupId);

    /**
     * Single-column projection of {@code UserAccount.tokenInvalidBefore} for the
     * {@code JwtAuthenticationFilter} revocation check ([RISK-21]). {@code UserAccount}
     * shares the user PK via {@code @MapsId}, so this hits one row/column without a
     * {@code JOIN} or a lazy {@code @OneToOne} proxy. Empty when the user or account
     * row is absent (deleted account / unknown subject).
     */
    @Query("select ua.tokenInvalidBefore from UserAccount ua where ua.id = :userId")
    Optional<OffsetDateTime> findTokenInvalidBefore(@Param("userId") UUID userId);
}
