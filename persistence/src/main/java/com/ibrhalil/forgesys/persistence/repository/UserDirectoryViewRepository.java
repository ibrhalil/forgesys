package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.UserDirectoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Read side of the flattened user directory ({@link UserDirectoryView} — join-run subselect,
 * no associations). All list/search reads go through Specifications (filter engine);
 * no {@code @EntityGraph} is needed or possible — the projection is already flat.
 */
@Repository
public interface UserDirectoryViewRepository extends JpaRepository<UserDirectoryView, UUID>, JpaSpecificationExecutor<UserDirectoryView> {
}
