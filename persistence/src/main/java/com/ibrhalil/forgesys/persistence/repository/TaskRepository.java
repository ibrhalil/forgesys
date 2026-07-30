package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** All tasks for a project (the board fetches the whole project's task set). */
    List<Task> findAllByProjectId(UUID projectId);

    /** Scoped lookup — a task is only reachable through its owning project. */
    Optional<Task> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByIdAndProjectId(UUID id, UUID projectId);
}
