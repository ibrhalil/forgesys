package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Paged tasks for a project (K-37 — the board fetches a large single page). */
    Page<Task> findAllByProjectId(UUID projectId, Pageable pageable);

    /** Scoped lookup — a task is only reachable through its owning project. */
    Optional<Task> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByIdAndProjectId(UUID id, UUID projectId);
}
