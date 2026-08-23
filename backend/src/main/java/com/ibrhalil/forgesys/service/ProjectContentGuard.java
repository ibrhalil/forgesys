package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.persistence.repository.NoteRepository;
import com.ibrhalil.forgesys.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Whether a typed project container holds content of its current type (K-45). While
 * it does, the project's type is locked ({@code project_type_change_forbidden}) —
 * mixed-content containers are the fragility this decision excludes. Tasks and notes
 * land here; apps join with their project-scoping migration (K-45 step 5).
 */
@Component
@RequiredArgsConstructor
public class ProjectContentGuard {

    private final TaskRepository taskRepository;
    private final NoteRepository noteRepository;

    public boolean hasContent(UUID projectId) {
        return taskRepository.existsByProjectId(projectId) || noteRepository.existsByProjectId(projectId);
    }
}
