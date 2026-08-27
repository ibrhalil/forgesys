package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.persistence.repository.CustomAppRepository;
import com.ibrhalil.forgesys.persistence.repository.NoteRepository;
import com.ibrhalil.forgesys.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Whether a typed container holds content of its current type (K-45) — while it does,
 * the type is locked ({@code project_type_change_forbidden}). One check per content
 * module: tasks (pm), notes (notes), apps (apps).
 */
@Component
@RequiredArgsConstructor
public class ProjectContentGuard {

    private final TaskRepository taskRepository;
    private final NoteRepository noteRepository;
    private final CustomAppRepository customAppRepository;

    public boolean hasContent(UUID projectId) {
        return taskRepository.existsByProjectId(projectId)
                || noteRepository.existsByProjectId(projectId)
                || customAppRepository.existsByProjectId(projectId);
    }
}
