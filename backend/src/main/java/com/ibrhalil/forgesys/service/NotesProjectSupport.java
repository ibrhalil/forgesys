package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the NOTES-type project container behind the notes module (K-45): nested
 * endpoints and writes go through {@link #assertNotesProject(UUID)} (404 unknown,
 * 409 type mismatch); the flat top-nav flow falls back to the per-tenant default
 * container ("Genel", ensured by {@code module/notes/V2} and module activation).
 */
@Component
@RequiredArgsConstructor
public class NotesProjectSupport {

    private static final ProjectType TYPE = ProjectType.NOTES;

    private final ProjectRepository projectRepository;

    /** The project must exist and be a NOTES container (else 409 {@code project_type_mismatch}). */
    public Project assertNotesProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (project.getType() != TYPE) {
            throw new BusinessException(ErrorCode.PROJECT_TYPE_MISMATCH,
                    "Project %s is not a %s container but %s".formatted(projectId, TYPE, project.getType()));
        }
        return project;
    }

    /** The per-tenant default NOTES container — top-nav fallback target. */
    public Project defaultNotesProject() {
        return projectRepository.findDefaultIdsByType(TYPE).stream()
                .findFirst()
                .flatMap(projectRepository::findById)
                .orElseThrow(() -> new ResourceNotFoundException("No default notes project for this tenant"));
    }

    /** Explicit target when provided, the default container otherwise. */
    public Project resolveTargetProject(UUID requestedProjectId) {
        return requestedProjectId != null ? assertNotesProject(requestedProjectId) : defaultNotesProject();
    }
}
