package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID>, JpaSpecificationExecutor<Note> {
}
