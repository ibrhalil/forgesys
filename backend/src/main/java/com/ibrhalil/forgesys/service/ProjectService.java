package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.ProjectRequest;
import com.ibrhalil.forgesys.dto.ProjectResponse;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.Project_;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped project (workspace) CRUD — the first product feature layer. A project's
 * {@code type} decides which modules are surfaced inside it (type-driven module system).
 * Same soft-delete + audit pattern as the IAM services.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    /** Filterable/sortable direct attributes of the project list; {@code q} matches {@code name}. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Project_.NAME, FilterFieldType.STRING, true)
            .field(Project_.DESCRIPTION, FilterFieldType.STRING, false)
            .enumField(Project_.TYPE, ProjectType.class, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<ProjectResponse> search(String q, Pageable pageable) {
        Specification<Project> spec = FilterSpecifications.from(FILTER_FIELDS, StringUtils.hasText(q) ? q.trim() : null, List.of());
        return projectRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(UUID id) {
        return toResponse(getProjectOrThrow(id));
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        if (projectRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_TAKEN, "Project name already exists: " + request.name());
        }
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setType(request.type());
        Project saved = projectRepository.save(project);
        auditService.record("project_created", "Project", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public ProjectResponse update(UUID id, ProjectRequest request) {
        Project project = getProjectOrThrow(id);
        if (!project.getName().equals(request.name()) && projectRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_TAKEN, "Project name already exists: " + request.name());
        }
        project.setName(request.name());
        project.setDescription(request.description());
        project.setType(request.type());
        Project saved = projectRepository.save(project);
        auditService.record("project_updated", "Project", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Project not found: " + id);
        }
        projectRepository.deleteById(id);
        auditService.record("project_deleted", "Project", id, null);
    }

    private Project getProjectOrThrow(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getType());
    }
}
