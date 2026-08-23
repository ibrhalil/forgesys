package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID>, JpaSpecificationExecutor<Note> {

    /** Whether the project holds any note — locks the project type while content exists (K-45). */
    boolean existsByProjectId(UUID projectId);
}
