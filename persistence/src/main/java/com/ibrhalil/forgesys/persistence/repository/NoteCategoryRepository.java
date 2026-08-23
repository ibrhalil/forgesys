package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.NoteCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoteCategoryRepository extends JpaRepository<NoteCategory, UUID>, JpaSpecificationExecutor<NoteCategory> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /** Category lookup scoped to its container — the note/category consistency check (K-45). */
    Optional<NoteCategory> findByIdAndProjectId(UUID id, UUID projectId);
}
