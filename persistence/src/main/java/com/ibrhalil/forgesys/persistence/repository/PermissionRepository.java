package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    boolean existsByName(String name);

    Optional<Permission> findByName(String name);

    List<Permission> findAllByNameIn(Collection<String> names);

    List<Permission> findAllByOrderByNameAsc();
}
