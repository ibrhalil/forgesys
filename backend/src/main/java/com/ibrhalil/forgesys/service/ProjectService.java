package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.common.exception.TenantNotFoundException;
import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.dto.ProjectRequest;
import com.ibrhalil.forgesys.dto.ProjectResponse;
import com.ibrhalil.forgesys.dto.ProjectTypeResponse;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.ModuleStatus;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.Project_;
import com.ibrhalil.forgesys.entity.TenantModule;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
import com.ibrhalil.forgesys.persistence.repository.TaskRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantModuleRepository;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterOperator;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tenant-scoped typed project container CRUD (K-45). The {@code type} decides which
 * module's content lives inside the project; the creatable catalog derives from the
 * tenant's ACTIVE modules ({@link #listAvailableTypes()}). Write guards: a parent must
 * exist and must not create a containment cycle; the type is locked while the project
 * holds content; the per-type default container's type and parent are frozen. Same
 * soft-delete + audit pattern as the IAM services.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    /** Filterable/sortable direct attributes of the project list; {@code q} matches {@code name}. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Project_.NAME, FilterFieldType.STRING, true)
            .field(Project_.DESCRIPTION, FilterFieldType.STRING, false)
            .enumField(Project_.TYPE, ProjectType.class, false)
            .field(Project_.PARENT_PROJECT_ID, FilterFieldType.UUID, false)
            .field(Project_.IS_DEFAULT, FilterFieldType.BOOLEAN, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    /** Defensive cap for the ancestor-chain walk on re-parent (K-45: no unbounded traversal). */
    private static final int MAX_PARENT_DEPTH = 50;

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TenantModuleRepository tenantModuleRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<ProjectResponse> search(String q, UUID parentProjectId, Pageable pageable) {
        List<FilterCriteria> filters = new ArrayList<>();
        if (parentProjectId != null) {
            filters.add(new FilterCriteria(Project_.PARENT_PROJECT_ID, FilterOperator.EQ, List.of(parentProjectId.toString())));
        }
        Specification<Project> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, filters);
        return projectRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /**
     * The creatable project-type catalog for the current tenant (K-45): one entry per
     * ACTIVE module that supplies a project type, with the per-type default container
     * id as the top-nav fallback target.
     */
    @Transactional(readOnly = true)
    public List<ProjectTypeResponse> listAvailableTypes() {
        Company company = currentCompany();
        Set<String> activeKeys = tenantModuleRepository.findByCompanyId(company.getId()).stream()
                .filter(row -> row.getStatus() == ModuleStatus.ACTIVE)
                .map(TenantModule::getModuleKey)
                .collect(Collectors.toSet());
        return Arrays.stream(ModuleDefinition.values())
                .filter(module -> module.projectType() != null && activeKeys.contains(module.key()))
                .map(module -> new ProjectTypeResponse(module.projectType(), module.key(),
                        defaultProjectId(module.projectType())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(UUID id) {
        return toResponse(getProjectOrThrow(id));
    }

    @Transactional
    @AuditLog(action = "project_created", entityType = "Project", entityId = "#result.id", entityName = "#result.name")
    public ProjectResponse create(ProjectRequest request) {
        if (projectRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_TAKEN, "Project name already exists: " + request.name());
        }
        assertParentAcceptable(request.parentProjectId(), null);
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setType(request.type());
        project.setParentProjectId(request.parentProjectId());
        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "project_updated", entityType = "Project", entityId = "#result.id", entityName = "#result.name")
    public ProjectResponse update(UUID id, ProjectRequest request) {
        Project project = getProjectOrThrow(id);
        if (!project.getName().equals(request.name()) && projectRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_TAKEN, "Project name already exists: " + request.name());
        }
        boolean typeChange = request.type() != project.getType();
        boolean parentChange = !Objects.equals(request.parentProjectId(), project.getParentProjectId());
        if (project.isDefault() && (typeChange || parentChange)) {
            throw new BusinessException(ErrorCode.PROJECT_DEFAULT_IMMUTABLE,
                    "The default project's type and parent are frozen: " + id);
        }
        if (typeChange && projectHasContent(id)) {
            throw new BusinessException(ErrorCode.PROJECT_TYPE_CHANGE_FORBIDDEN,
                    "Project holds content of its current type; type change rejected: " + id);
        }
        if (parentChange) {
            assertParentAcceptable(request.parentProjectId(), id);
        }
        project.setName(request.name());
        project.setDescription(request.description());
        project.setType(request.type());
        project.setParentProjectId(request.parentProjectId());
        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "project_deleted", entityType = "Project", entityId = "#id", entityName = "")
    public void delete(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Project not found: " + id);
        }
        projectRepository.deleteById(id);
    }

    /**
     * Whether the project holds content of its current type. Tasks land here (K-45
     * step 2); note/app checks join with their project-scoping migrations.
     */
    private boolean projectHasContent(UUID projectId) {
        return taskRepository.existsByProjectId(projectId);
    }

    /**
     * A new parent must exist and must not put the project inside itself. The ancestor
     * chain is walked Hibernate-visibly (soft-deleted mid-chain rows end the walk — a
     * deleted node's frozen parent link cannot be extended by this update, so no cycle
     * can be created through it). Depth-capped: an over-deep chain is rejected outright.
     */
    private void assertParentAcceptable(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BusinessException(ErrorCode.PROJECT_CYCLE_FORBIDDEN, "A project cannot be its own parent: " + selfId);
        }
        if (!projectRepository.existsById(parentId)) {
            throw new ResourceNotFoundException("Parent project not found: " + parentId);
        }
        UUID cursor = parentId;
        for (int depth = 0; cursor != null && depth < MAX_PARENT_DEPTH; depth++) {
            if (cursor.equals(selfId)) {
                throw new BusinessException(ErrorCode.PROJECT_CYCLE_FORBIDDEN,
                        "Parenting would create a containment cycle: " + selfId + " under " + parentId);
            }
            cursor = projectRepository.findById(cursor).map(Project::getParentProjectId).orElse(null);
        }
        if (cursor != null) {
            throw new BusinessException(ErrorCode.PROJECT_CYCLE_FORBIDDEN,
                    "Parent chain exceeds the maximum depth of " + MAX_PARENT_DEPTH);
        }
    }

    private UUID defaultProjectId(ProjectType type) {
        return projectRepository.findDefaultIdsByType(type).stream().findFirst().orElse(null);
    }

    private Company currentCompany() {
        String schemaName = TenantContext.getCurrentTenant()
                .orElseThrow(() -> new TenantNotFoundException("Tenant context is not set"));
        return companyRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new TenantNotFoundException("Unknown tenant schema: " + schemaName));
    }

    private Project getProjectOrThrow(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getType(),
                project.getParentProjectId(), project.isDefault());
    }
}
