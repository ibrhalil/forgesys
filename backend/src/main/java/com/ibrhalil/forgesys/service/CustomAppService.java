package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.CustomAppDetailResponse;
import com.ibrhalil.forgesys.dto.CustomAppPropertyConfigDto;
import com.ibrhalil.forgesys.dto.CustomAppPropertyRequest;
import com.ibrhalil.forgesys.dto.CustomAppPropertyResponse;
import com.ibrhalil.forgesys.dto.CustomAppRequest;
import com.ibrhalil.forgesys.dto.CustomAppResponse;
import com.ibrhalil.forgesys.dto.CustomAppViewConfigDto;
import com.ibrhalil.forgesys.dto.CustomAppViewRequest;
import com.ibrhalil.forgesys.dto.CustomAppViewResponse;
import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.entity.CustomApp;
import com.ibrhalil.forgesys.entity.CustomAppProperty;
import com.ibrhalil.forgesys.entity.CustomAppView;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.CustomApp_;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.CustomAppPropertyRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppViewRepository;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
import com.ibrhalil.forgesys.audit.AuditLog;
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
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Custom customApp definition CRUD (K-15/K-45): apps in APPS-type containers, their
 * properties (columns) and views; record CRUD lives in {@link CustomAppRecordService}.
 * Plan limits are tenant-level soft-blocks ({@link PlanLimitService}); same TOCTOU
 * posture as the other services (RISK-28). Rationale: docs/CODE_NOTES.md (backend/service → CustomAppService).
 */
@Service
@RequiredArgsConstructor
public class CustomAppService {

    private static final int MAX_SELECT_OPTIONS = 100;
    private static final int MAX_OPTION_LENGTH = 100;

    /**
     * Filterable/sortable attributes of the customApp list (K-49); {@code q} matches
     * {@code name}, {@code description} and the resolved container name
     * ({@code projectName} — correlated subquery, K-45 convention).
     */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(CustomApp_.NAME, FilterFieldType.STRING, true)
            .field(CustomApp_.DESCRIPTION, FilterFieldType.STRING, true)
            .field(CustomApp_.ICON, FilterFieldType.STRING, false)
            .field(CustomApp_.PROJECT_ID, FilterFieldType.UUID, false)
            .subqueryField("projectName", FilterFieldType.STRING, true,
                    NoteListQueryExecutor.projectNameOf(CustomApp_.PROJECT_ID))
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final CustomAppRepository customAppRepository;
    private final CustomAppListQueryExecutor customAppListQueryExecutor;
    private final CustomAppPropertyRepository propertyRepository;
    private final CustomAppViewRepository viewRepository;
    private final CustomAppViewConfigValidator viewConfigValidator;
    private final PlanLimitService planLimitService;
    private final ProjectContainerSupport projectContainerSupport;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // ── apps ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CustomAppResponse> search(String q, List<String> qFields, UUID projectId, Pageable pageable) {
        List<FilterCriteria> filters = projectId == null ? List.of()
                : List.of(new FilterCriteria(CustomApp_.PROJECT_ID, FilterOperator.EQ, List.of(projectId.toString())));
        return doSearch(q, qFields, filters, pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /apps/search}. */
    @Transactional(readOnly = true)
    public Page<CustomAppResponse> search(SearchRequest request, Pageable pageable) {
        return doSearch(request.q(), request.qFields(), request.filters(), pageable);
    }

    private Page<CustomAppResponse> doSearch(String q, List<String> qFields, List<FilterCriteria> filters,
            Pageable pageable) {
        Specification<CustomApp> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, qFields, filters);
        return customAppListQueryExecutor.search(spec, pageable);
    }

    /** Cross-container list narrowed to one APPS container (nested endpoint, K-45). */
    @Transactional(readOnly = true)
    public Page<CustomAppResponse> searchInProject(UUID projectId, String q, List<String> qFields, Pageable pageable) {
        projectContainerSupport.assertProject(ProjectType.APPS, projectId);
        return search(q, qFields, projectId, pageable);
    }

    @Transactional(readOnly = true)
    public CustomAppDetailResponse findById(UUID id) {
        CustomApp customApp = getCustomAppOrThrow(id);
        List<CustomAppPropertyResponse> properties = propertyRepository
                .findAllByCustomAppIdOrderByPositionAscNameAsc(id).stream().map(this::toResponse).toList();
        List<CustomAppViewResponse> views = viewRepository
                .findAllByCustomAppIdOrderByPositionAscNameAsc(id).stream().map(this::toResponse).toList();
        String projectName = resolveProjectNames(List.of(customApp.getProjectId())).get(customApp.getProjectId());
        return new CustomAppDetailResponse(customApp.getId(), customApp.getName(), customApp.getDescription(), customApp.getIcon(),
                customApp.getProjectId(), projectName, customApp.getCreatedDate(), customApp.getUpdatedAt(), properties, views);
    }

    @Transactional
    @AuditLog(action = "custom_app_created", entityType = "CustomApp", entityId = "#result.id", entityName = "#result.name")
    public CustomAppResponse create(CustomAppRequest request) {
        Project target = projectContainerSupport.resolveTarget(ProjectType.APPS, request.projectId());
        return createIn(target, request);
    }

    /** Nested create (K-45): the project must be an APPS container (404/409 otherwise). */
    @Transactional
    @AuditLog(action = "custom_app_created", entityType = "CustomApp", entityId = "#result.id", entityName = "#result.name")
    public CustomAppResponse createInProject(UUID projectId, CustomAppRequest request) {
        Project target = projectContainerSupport.assertProject(ProjectType.APPS, projectId);
        return createIn(target, request);
    }

    private CustomAppResponse createIn(Project target, CustomAppRequest request) {
        if (customAppRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_NAME_TAKEN, "Custom app name already exists: " + request.name());
        }
        planLimitService.assertWithin(customAppRepository.count(), planLimitService.maxCustomApps(), "custom apps");
        CustomApp customApp = new CustomApp();
        customApp.setName(request.name());
        customApp.setDescription(request.description());
        customApp.setIcon(request.icon());
        customApp.setProjectId(target.getId());
        CustomApp saved = customAppRepository.save(customApp);
        return toResponse(saved, target.getName());
    }

    @Transactional
    @AuditLog(action = "custom_app_updated", entityType = "CustomApp", entityId = "#result.id", entityName = "#result.name")
    public CustomAppResponse update(UUID id, CustomAppRequest request) {
        CustomApp customApp = getCustomAppOrThrow(id);
        if (!customApp.getName().equals(request.name()) && customAppRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_NAME_TAKEN, "Custom app name already exists: " + request.name());
        }
        if (request.projectId() != null && !request.projectId().equals(customApp.getProjectId())) {
            customApp.setProjectId(projectContainerSupport.assertProject(ProjectType.APPS, request.projectId()).getId());
        }
        customApp.setName(request.name());
        customApp.setDescription(request.description());
        customApp.setIcon(request.icon());
        CustomApp saved = customAppRepository.save(customApp);
        String projectName = resolveProjectNames(List.of(saved.getProjectId())).get(saved.getProjectId());
        return toResponse(saved, projectName);
    }

    @Transactional
    @AuditLog(action = "custom_app_deleted", entityType = "CustomApp", entityId = "#id", entityName = "")
    public void delete(UUID id) {
        CustomApp customApp = getCustomAppOrThrow(id);
        customAppRepository.delete(customApp);
    }

    // ── properties ──────────────────────────────────────────────────────

    @Transactional
    @AuditLog(action = "custom_app_property_created", entityType = "CustomAppProperty", entityId = "#result.id", entityName = "#result.name")
    public CustomAppPropertyResponse addProperty(UUID customAppId, CustomAppPropertyRequest request) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        validatePropertyDefinition(request);
        if (propertyRepository.existsByCustomAppIdAndName(customAppId, request.name())) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_NAME_TAKEN,
                    "Property name already exists in this custom app: " + request.name());
        }
        CustomAppProperty property = new CustomAppProperty();
        property.setCustomAppId(customAppId);
        applyPropertyRequest(property, request);
        // Absent position appends at the end (first property = 0).
        if (request.position() == null) {
            property.setPosition(nextPosition(propertyRepository.findMaxPosition(customAppId)));
        }
        CustomAppProperty saved = propertyRepository.save(property);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "custom_app_property_updated", entityType = "CustomAppProperty", entityId = "#result.id", entityName = "#result.name")
    public CustomAppPropertyResponse updateProperty(UUID customAppId, UUID propertyId, CustomAppPropertyRequest request) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        CustomAppProperty property = getPropertyOrThrow(customAppId, propertyId);
        if (request.type() != property.getType()) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_TYPE_INVALID,
                    "Property type is immutable; delete and recreate the property");
        }
        validatePropertyDefinition(request);
        if (!property.getName().equals(request.name())
                && propertyRepository.existsByCustomAppIdAndName(customAppId, request.name())) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_NAME_TAKEN,
                    "Property name already exists in this custom app: " + request.name());
        }
        applyPropertyRequest(property, request);
        CustomAppProperty saved = propertyRepository.save(property);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "custom_app_property_deleted", entityType = "CustomAppProperty", entityId = "#propertyId", entityName = "")
    public void deleteProperty(UUID customAppId, UUID propertyId) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        CustomAppProperty property = getPropertyOrThrow(customAppId, propertyId);
        propertyRepository.delete(property);
        // Value rows are dependent data — meaningless once the definition is gone.
        propertyRepository.deleteValuesByPropertyId(propertyId);
    }

    // ── views ───────────────────────────────────────────────────────────

    @Transactional
    @AuditLog(action = "custom_app_view_created", entityType = "CustomAppView", entityId = "#result.id", entityName = "#result.name")
    public CustomAppViewResponse addView(UUID customAppId, CustomAppViewRequest request) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        if (viewRepository.existsByCustomAppIdAndName(customAppId, request.name())) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_VIEW_NAME_TAKEN,
                    "View name already exists in this custom app: " + request.name());
        }
        Map<UUID, CustomAppProperty> properties = CustomAppQueryValidator.byId(
                propertyRepository.findAllByCustomAppIdOrderByPositionAscNameAsc(customAppId));
        String config = viewConfigValidator.validateAndSerialize(request.type(), request.config(), properties);

        CustomAppView view = new CustomAppView();
        view.setCustomAppId(customAppId);
        view.setName(request.name());
        view.setType(request.type());
        view.setConfig(config);
        // Absent position appends at the end (first view = 0).
        view.setPosition(request.position() != null ? request.position()
                : nextPosition(viewRepository.findMaxPosition(customAppId)));
        CustomAppView saved = viewRepository.save(view);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "custom_app_view_updated", entityType = "CustomAppView", entityId = "#result.id", entityName = "#result.name")
    public CustomAppViewResponse updateView(UUID customAppId, UUID viewId, CustomAppViewRequest request) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        CustomAppView view = getViewOrThrow(customAppId, viewId);
        if (!view.getName().equals(request.name())
                && viewRepository.existsByCustomAppIdAndName(customAppId, request.name())) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_VIEW_NAME_TAKEN,
                    "View name already exists in this custom app: " + request.name());
        }
        Map<UUID, CustomAppProperty> properties = CustomAppQueryValidator.byId(
                propertyRepository.findAllByCustomAppIdOrderByPositionAscNameAsc(customAppId));
        String config = viewConfigValidator.validateAndSerialize(request.type(), request.config(), properties);

        view.setName(request.name());
        view.setType(request.type());
        view.setConfig(config);
        // Null position = keep the current tab order (partial-PUT semantics).
        if (request.position() != null) {
            view.setPosition(request.position());
        }
        CustomAppView saved = viewRepository.save(view);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "custom_app_view_deleted", entityType = "CustomAppView", entityId = "#viewId", entityName = "")
    public void deleteView(UUID customAppId, UUID viewId) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        CustomAppView view = getViewOrThrow(customAppId, viewId);
        viewRepository.delete(view);
    }

    // ── definition validation ───────────────────────────────────────────

    /**
     * Type-vs-config validation: SELECT needs a non-empty, distinct, bounded option
     * set; RELATION an existing target customApp; other types take no config; FORMULA is
     * rejected outright (deferred).
     */
    private void validatePropertyDefinition(CustomAppPropertyRequest request) {
        if (request.type() == PropertyType.FORMULA) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_TYPE_INVALID,
                    "FORMULA properties are not supported yet (deferred — see ROADMAP 3.0.B)");
        }
        CustomAppPropertyConfigDto config = request.config();
        switch (request.type()) {
            case SELECT -> {
                if (config == null || config.options() == null || config.options().isEmpty()) {
                    throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                            "SELECT properties require config.options with at least one option");
                }
                if (config.options().size() > MAX_SELECT_OPTIONS) {
                    throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                            "SELECT properties allow at most " + MAX_SELECT_OPTIONS + " options");
                }
                for (String option : config.options()) {
                    if (option == null || option.isBlank()) {
                        throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                                "SELECT options must be non-empty strings");
                    }
                    if (option.length() > MAX_OPTION_LENGTH) {
                        throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                                "SELECT options must be at most " + MAX_OPTION_LENGTH + " characters");
                    }
                }
                if (new LinkedHashSet<>(config.options()).size() != config.options().size()) {
                    throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                            "SELECT options must be distinct");
                }
                rejectUnexpectedTargetApp(request, "SELECT");
            }
            case RELATION -> {
                if (config == null || config.targetCustomAppId() == null || config.targetCustomAppId().isBlank()) {
                    throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                            "RELATION properties require config.targetCustomAppId");
                }
                UUID targetCustomAppId;
                try {
                    targetCustomAppId = UUID.fromString(config.targetCustomAppId());
                } catch (IllegalArgumentException ex) {
                    throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                            "config.targetCustomAppId must be a UUID string");
                }
                if (!customAppRepository.existsById(targetCustomAppId)) {
                    throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                            "config.targetCustomAppId does not reference an existing custom app");
                }
                if (config.options() != null && !config.options().isEmpty()) {
                    throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                            "RELATION properties take no options");
                }
            }
            case TEXT, NUMBER, DATE, USER -> {
                if (config != null && ((config.options() != null && !config.options().isEmpty())
                        || (config.targetCustomAppId() != null && !config.targetCustomAppId().isBlank()))) {
                    throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                            request.type() + " properties take no config");
                }
            }
            case FORMULA -> throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_TYPE_INVALID,
                    "FORMULA properties are not supported yet (deferred — see ROADMAP 3.0.B)");
        }
    }

    private void rejectUnexpectedTargetApp(CustomAppPropertyRequest request, String type) {
        CustomAppPropertyConfigDto config = request.config();
        if (config != null && config.targetCustomAppId() != null && !config.targetCustomAppId().isBlank()) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                    type + " properties take no targetCustomAppId");
        }
    }

    private void applyPropertyRequest(CustomAppProperty property, CustomAppPropertyRequest request) {
        property.setName(request.name());
        property.setType(request.type());
        property.setRequired(request.required());
        // Null position = keep the current value (partial-PUT semantics).
        if (request.position() != null) {
            property.setPosition(request.position());
        }
        property.setConfig(serializeConfig(request.config()));
    }

    /** 0-based append position for a new child (max+1; 0 when the customApp has none). */
    private int nextPosition(Integer max) {
        return max == null ? 0 : max + 1;
    }

    private String serializeConfig(CustomAppPropertyConfigDto config) {
        if (config == null) {
            return null;
        }
        boolean empty = (config.options() == null || config.options().isEmpty())
                && (config.targetCustomAppId() == null || config.targetCustomAppId().isBlank());
        return empty ? null : objectMapper.writeValueAsString(config);
    }

    // ── lookups & mappers ───────────────────────────────────────────────

    private CustomApp getCustomAppOrThrow(UUID id) {
        return customAppRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomApp not found: " + id));
    }

    private CustomAppProperty getPropertyOrThrow(UUID customAppId, UUID propertyId) {
        return propertyRepository.findByIdAndCustomAppId(propertyId, customAppId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));
    }

    private CustomAppView getViewOrThrow(UUID customAppId, UUID viewId) {
        return viewRepository.findByIdAndCustomAppId(viewId, customAppId)
                .orElseThrow(() -> new ResourceNotFoundException("View not found: " + viewId));
    }

    private CustomAppResponse toResponse(CustomApp customApp, String projectName) {
        return new CustomAppResponse(customApp.getId(), customApp.getName(), customApp.getDescription(), customApp.getIcon(),
                customApp.getProjectId(), projectName, customApp.getCreatedDate(), customApp.getUpdatedAt());
    }

    /** Batched project-name resolution for a page of apps (one query per page, no per-row lookups). */
    private Map<UUID, String> resolveProjectNames(List<UUID> projectIds) {
        LinkedHashMap<UUID, String> names = new LinkedHashMap<>();
        for (UUID id : projectIds) {
            if (id != null) {
                names.put(id, null);
            }
        }
        if (names.isEmpty()) {
            return Map.of();
        }
        for (Project project : projectRepository.findAllById(names.keySet())) {
            names.put(project.getId(), project.getName());
        }
        return names;
    }

    private CustomAppPropertyResponse toResponse(CustomAppProperty property) {
        return new CustomAppPropertyResponse(property.getId(), property.getCustomAppId(), property.getName(),
                property.getType(), parseConfig(property.getConfig()), property.isRequired(),
                property.getPosition());
    }

    private CustomAppViewResponse toResponse(CustomAppView view) {
        return new CustomAppViewResponse(view.getId(), view.getCustomAppId(), view.getName(), view.getType(),
                parseViewConfig(view.getConfig()), view.getPosition());
    }

    private CustomAppPropertyConfigDto parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return null;
        }
        return objectMapper.readValue(config, CustomAppPropertyConfigDto.class);
    }

    private CustomAppViewConfigDto parseViewConfig(String config) {
        if (config == null || config.isBlank()) {
            return null;
        }
        return objectMapper.readValue(config, CustomAppViewConfigDto.class);
    }
}
