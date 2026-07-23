package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    boolean existsByName(String name);

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findByName(String name);

    /**
     * {@inheritDoc}
     * Redeclared to attach an {@link EntityGraph} (permissions) so the paginated list
     * query avoids N+1 when building {@code RoleResponse}.
     */
    @Override
    @EntityGraph(attributePaths = "permissions")
    Page<Role> findAll(Pageable pageable);
}
