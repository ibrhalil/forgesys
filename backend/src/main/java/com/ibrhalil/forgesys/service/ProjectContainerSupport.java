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
 * Resolves typed project containers for the content modules (K-45):
 * {@link #assertProject} for nested endpoints/writes (404 unknown, 409 type
 * mismatch); the per-type default container ("Genel") as the flat top-nav fallback.
 * Shared by the notes and apps modules.
 */
@Component
@RequiredArgsConstructor
public class ProjectContainerSupport {

    private final ProjectRepository projectRepository;

    /** The project must exist and carry the expected content type (else 409 {@code project_type_mismatch}). */
    public Project assertProject(ProjectType expectedType, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (project.getType() != expectedType) {
            throw new BusinessException(ErrorCode.PROJECT_TYPE_MISMATCH,
                    "Project %s is not a %s container but %s".formatted(projectId, expectedType, project.getType()));
        }
        return project;
    }

    /** The per-tenant default container of the given type — top-nav fallback target. */
    public Project defaultProject(ProjectType type) {
        return projectRepository.findDefaultIdsByType(type).stream()
                .findFirst()
                .flatMap(projectRepository::findById)
                .orElseThrow(() -> new ResourceNotFoundException("No default project of type " + type + " for this tenant"));
    }

    /** Explicit target when provided, the default container otherwise. */
    public Project resolveTarget(ProjectType type, UUID requestedProjectId) {
        return requestedProjectId != null ? assertProject(type, requestedProjectId) : defaultProject(type);
    }
}
