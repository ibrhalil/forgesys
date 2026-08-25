package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.common.exception.TenantNotFoundException;
import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.dto.ProjectRequest;
import com.ibrhalil.forgesys.dto.ProjectResponse;
import com.ibrhalil.forgesys.dto.ProjectTypeResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
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

    /**
     * Filterable/sortable attributes of the project list (K-49); {@code q} matches
     * {@code name}, {@code description} and the resolved parent name.
     * {@code parentProjectName} is a correlated scalar subquery over the plain self-FK
     * column — a soft-deleted parent resolves to null.
     */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Project_.NAME, FilterFieldType.STRING, true)
            .field(Project_.DESCRIPTION, FilterFieldType.STRING, true)
            .enumField(Project_.TYPE, ProjectType.class, false)
            .field(Project_.PARENT_PROJECT_ID, FilterFieldType.UUID, false)
            .subqueryField("parentProjectName", FilterFieldType.STRING, true,
                    NoteListQueryExecutor.projectNameOf(Project_.PARENT_PROJECT_ID))
            .field(Project_.IS_DEFAULT, FilterFieldType.BOOLEAN, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    /** Defensive cap for the ancestor-chain walk on re-parent (K-45: no unbounded traversal). */
    private static final int MAX_PARENT_DEPTH = 50;

    private final ProjectRepository projectRepository;
    private final ProjectListQueryExecutor projectListQueryExecutor;
    private final ProjectContentGuard projectContentGuard;
    private final TenantModuleRepository tenantModuleRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<ProjectResponse> search(String q, List<String> qFields, UUID parentProjectId, ProjectType type,
            Pageable pageable) {
        List<FilterCriteria> filters = new ArrayList<>();
        if (parentProjectId != null) {
            filters.add(new FilterCriteria(Project_.PARENT_PROJECT_ID, FilterOperator.EQ, List.of(parentProjectId.toString())));
        }
        if (type != null) {
            filters.add(new FilterCriteria(Project_.TYPE, FilterOperator.EQ, List.of(type.name())));
        }
        return doSearch(q, qFields, filters, pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /projects/search}. */
    @Transactional(readOnly = true)
    public Page<ProjectResponse> search(SearchRequest request, Pageable pageable) {
        return doSearch(request.q(), request.qFields(), request.filters(), pageable);
    }

    private Page<ProjectResponse> doSearch(String q, List<String> qFields, List<FilterCriteria> filters,
            Pageable pageable) {
        Specification<Project> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, qFields, filters);
        return projectListQueryExecutor.search(spec, pageable);
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
        if (projectRepository.existsByNameAndType(request.name(), request.type())) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_TAKEN, "Project name already exists: " + request.name());
        }
        assertParentAcceptable(request.parentProjectId(), null);
        assertTypeActivatable(request.type());
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
        if (!project.getName().equals(request.name())
                && projectRepository.existsByNameAndTypeAndIdNot(request.name(), request.type(), id)) {
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
        if (typeChange) {
            assertTypeActivatable(request.type());
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
     * Delegates to {@link ProjectContentGuard}: tasks + notes today, apps with their
     * project-scoping migration (K-45 step 5).
     */
    private boolean projectHasContent(UUID projectId) {
        return projectContentGuard.hasContent(projectId);
    }

    /**
     * K-45 activation gate: a project type is only creatable while the module that
     * supplies its content is ACTIVE for the tenant. Applied on create (always) and on
     * update (only when the type actually changes — a rename of a project whose module
     * went inactive stays allowed; its content is merely read-only elsewhere).
     */
    private void assertTypeActivatable(ProjectType type) {
        ModuleDefinition module = ModuleDefinition.forProjectType(type)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "No module supplies project type: " + type));
        Company company = currentCompany();
        boolean active = tenantModuleRepository.findByCompanyIdAndModuleKey(company.getId(), module.key())
                .map(row -> row.getStatus() == ModuleStatus.ACTIVE)
                .orElse(false);
        if (!active) {
            throw new BusinessException(ErrorCode.MODULE_NOT_ACTIVE,
                    "Module '%s' (project type %s) is not active for this tenant".formatted(module.key(), type));
        }
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

    /**
     * The company owning the current tenant schema. Mirrors the Hibernate resolver's
     * fallback ({@code public} when the context is unset — the H2 test layout); in
     * production the filter always sets the context and no company owns "public",
     * so the lookup degrades to {@link TenantNotFoundException}.
     */
    private Company currentCompany() {
        String schemaName = TenantContext.getCurrentTenant().orElse("public");
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
