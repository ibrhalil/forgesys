package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AppDetailResponse;
import com.ibrhalil.forgesys.dto.AppPropertyConfigDto;
import com.ibrhalil.forgesys.dto.AppPropertyRequest;
import com.ibrhalil.forgesys.dto.AppPropertyResponse;
import com.ibrhalil.forgesys.dto.AppRequest;
import com.ibrhalil.forgesys.dto.AppResponse;
import com.ibrhalil.forgesys.dto.AppViewConfigDto;
import com.ibrhalil.forgesys.dto.AppViewRequest;
import com.ibrhalil.forgesys.dto.AppViewResponse;
import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.entity.App;
import com.ibrhalil.forgesys.entity.AppProperty;
import com.ibrhalil.forgesys.entity.AppView;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.App_;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.AppPropertyRepository;
import com.ibrhalil.forgesys.persistence.repository.AppRepository;
import com.ibrhalil.forgesys.persistence.repository.AppViewRepository;
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
 * Custom app definition CRUD (K-15 / Epic 3.0.B, re-scoped by K-45): apps living in
 * APPS-type project containers, their properties (columns) and their views. Record
 * (row) CRUD lives in {@link AppRecordService}. Plan limits are soft-blocked on app
 * creation ({@link PlanLimitService}) and stay TENANT-level (not per container); the
 * flat list is the cross-container view ({@code ?projectId=} narrows it), writes
 * without an explicit {@code projectId} land in the default APPS container. Same
 * TOCTOU posture as the other services: {@code existsBy*} pre-check +
 * {@code DataIntegrityViolationException} constraint-map fallback (RISK-28).
 */
@Service
@RequiredArgsConstructor
public class AppBuilderService {

    private static final int MAX_SELECT_OPTIONS = 100;
    private static final int MAX_OPTION_LENGTH = 100;

    /** Filterable/sortable direct attributes of the app list; {@code q} matches {@code name}. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(App_.NAME, FilterFieldType.STRING, true)
            .field(App_.DESCRIPTION, FilterFieldType.STRING, false)
            .field(App_.PROJECT_ID, FilterFieldType.UUID, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final AppRepository appRepository;
    private final AppPropertyRepository propertyRepository;
    private final AppViewRepository viewRepository;
    private final AppViewConfigValidator viewConfigValidator;
    private final PlanLimitService planLimitService;
    private final ProjectContainerSupport projectContainerSupport;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // ── apps ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AppResponse> search(String q, UUID projectId, Pageable pageable) {
        List<FilterCriteria> filters = projectId == null ? List.of()
                : List.of(new FilterCriteria(App_.PROJECT_ID, FilterOperator.EQ, List.of(projectId.toString())));
        Specification<App> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, filters);
        Page<App> page = appRepository.findAll(spec, pageable);
        Map<UUID, String> projectNames = resolveProjectNames(page.stream().map(App::getProjectId).toList());
        return page.map(app -> toResponse(app, projectNames.get(app.getProjectId())));
    }

    /** Cross-container list narrowed to one APPS container (nested endpoint, K-45). */
    @Transactional(readOnly = true)
    public Page<AppResponse> searchInProject(UUID projectId, String q, Pageable pageable) {
        projectContainerSupport.assertProject(ProjectType.APPS, projectId);
        return search(q, projectId, pageable);
    }

    @Transactional(readOnly = true)
    public AppDetailResponse findById(UUID id) {
        App app = getAppOrThrow(id);
        List<AppPropertyResponse> properties = propertyRepository
                .findAllByAppIdOrderByPositionAscNameAsc(id).stream().map(this::toResponse).toList();
        List<AppViewResponse> views = viewRepository
                .findAllByAppIdOrderByPositionAscNameAsc(id).stream().map(this::toResponse).toList();
        String projectName = resolveProjectNames(List.of(app.getProjectId())).get(app.getProjectId());
        return new AppDetailResponse(app.getId(), app.getName(), app.getDescription(), app.getIcon(),
                app.getProjectId(), projectName, app.getCreatedDate(), app.getUpdatedAt(), properties, views);
    }

    @Transactional
    @AuditLog(action = "app_created", entityType = "App", entityId = "#result.id", entityName = "#result.name")
    public AppResponse create(AppRequest request) {
        Project target = projectContainerSupport.resolveTarget(ProjectType.APPS, request.projectId());
        return createIn(target, request);
    }

    /** Nested create (K-45): the project must be an APPS container (404/409 otherwise). */
    @Transactional
    @AuditLog(action = "app_created", entityType = "App", entityId = "#result.id", entityName = "#result.name")
    public AppResponse createInProject(UUID projectId, AppRequest request) {
        Project target = projectContainerSupport.assertProject(ProjectType.APPS, projectId);
        return createIn(target, request);
    }

    private AppResponse createIn(Project target, AppRequest request) {
        if (appRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.APP_NAME_TAKEN, "App name already exists: " + request.name());
        }
        planLimitService.assertWithin(appRepository.count(), planLimitService.maxApps(), "custom apps");
        App app = new App();
        app.setName(request.name());
        app.setDescription(request.description());
        app.setIcon(request.icon());
        app.setProjectId(target.getId());
        App saved = appRepository.save(app);
        return toResponse(saved, target.getName());
    }

    @Transactional
    @AuditLog(action = "app_updated", entityType = "App", entityId = "#result.id", entityName = "#result.name")
    public AppResponse update(UUID id, AppRequest request) {
        App app = getAppOrThrow(id);
        if (!app.getName().equals(request.name()) && appRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.APP_NAME_TAKEN, "App name already exists: " + request.name());
        }
        if (request.projectId() != null && !request.projectId().equals(app.getProjectId())) {
            app.setProjectId(projectContainerSupport.assertProject(ProjectType.APPS, request.projectId()).getId());
        }
        app.setName(request.name());
        app.setDescription(request.description());
        app.setIcon(request.icon());
        App saved = appRepository.save(app);
        String projectName = resolveProjectNames(List.of(saved.getProjectId())).get(saved.getProjectId());
        return toResponse(saved, projectName);
    }

    @Transactional
    @AuditLog(action = "app_deleted", entityType = "App", entityId = "#id", entityName = "")
    public void delete(UUID id) {
        App app = getAppOrThrow(id);
        appRepository.delete(app);
    }

    // ── properties ──────────────────────────────────────────────────────

    @Transactional
    @AuditLog(action = "app_property_created", entityType = "AppProperty", entityId = "#result.id", entityName = "#result.name")
    public AppPropertyResponse addProperty(UUID appId, AppPropertyRequest request) {
        App app = getAppOrThrow(appId);
        validatePropertyDefinition(request);
        if (propertyRepository.existsByAppIdAndName(appId, request.name())) {
            throw new BusinessException(ErrorCode.APP_PROPERTY_NAME_TAKEN,
                    "Property name already exists in this app: " + request.name());
        }
        AppProperty property = new AppProperty();
        property.setAppId(appId);
        applyPropertyRequest(property, request);
        // Absent position appends at the end (first property = 0).
        if (request.position() == null) {
            property.setPosition(nextPosition(propertyRepository.findMaxPosition(appId)));
        }
        AppProperty saved = propertyRepository.save(property);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "app_property_updated", entityType = "AppProperty", entityId = "#result.id", entityName = "#result.name")
    public AppPropertyResponse updateProperty(UUID appId, UUID propertyId, AppPropertyRequest request) {
        App app = getAppOrThrow(appId);
        AppProperty property = getPropertyOrThrow(appId, propertyId);
        if (request.type() != property.getType()) {
            throw new BusinessException(ErrorCode.APP_PROPERTY_TYPE_INVALID,
                    "Property type is immutable; delete and recreate the property");
        }
        validatePropertyDefinition(request);
        if (!property.getName().equals(request.name())
                && propertyRepository.existsByAppIdAndName(appId, request.name())) {
            throw new BusinessException(ErrorCode.APP_PROPERTY_NAME_TAKEN,
                    "Property name already exists in this app: " + request.name());
        }
        applyPropertyRequest(property, request);
        AppProperty saved = propertyRepository.save(property);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "app_property_deleted", entityType = "AppProperty", entityId = "#propertyId", entityName = "")
    public void deleteProperty(UUID appId, UUID propertyId) {
        App app = getAppOrThrow(appId);
        AppProperty property = getPropertyOrThrow(appId, propertyId);
        propertyRepository.delete(property);
        // Value rows are dependent data — meaningless once the definition is gone.
        propertyRepository.deleteValuesByPropertyId(propertyId);
    }

    // ── views ───────────────────────────────────────────────────────────

    @Transactional
    @AuditLog(action = "app_view_created", entityType = "AppView", entityId = "#result.id", entityName = "#result.name")
    public AppViewResponse addView(UUID appId, AppViewRequest request) {
        App app = getAppOrThrow(appId);
        if (viewRepository.existsByAppIdAndName(appId, request.name())) {
            throw new BusinessException(ErrorCode.APP_VIEW_NAME_TAKEN,
                    "View name already exists in this app: " + request.name());
        }
        Map<UUID, AppProperty> properties = AppQueryValidator.byId(
                propertyRepository.findAllByAppIdOrderByPositionAscNameAsc(appId));
        String config = viewConfigValidator.validateAndSerialize(request.type(), request.config(), properties);

        AppView view = new AppView();
        view.setAppId(appId);
        view.setName(request.name());
        view.setType(request.type());
        view.setConfig(config);
        // Absent position appends at the end (first view = 0).
        view.setPosition(request.position() != null ? request.position()
                : nextPosition(viewRepository.findMaxPosition(appId)));
        AppView saved = viewRepository.save(view);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "app_view_updated", entityType = "AppView", entityId = "#result.id", entityName = "#result.name")
    public AppViewResponse updateView(UUID appId, UUID viewId, AppViewRequest request) {
        App app = getAppOrThrow(appId);
        AppView view = getViewOrThrow(appId, viewId);
        if (!view.getName().equals(request.name())
                && viewRepository.existsByAppIdAndName(appId, request.name())) {
            throw new BusinessException(ErrorCode.APP_VIEW_NAME_TAKEN,
                    "View name already exists in this app: " + request.name());
        }
        Map<UUID, AppProperty> properties = AppQueryValidator.byId(
                propertyRepository.findAllByAppIdOrderByPositionAscNameAsc(appId));
        String config = viewConfigValidator.validateAndSerialize(request.type(), request.config(), properties);

        view.setName(request.name());
        view.setType(request.type());
        view.setConfig(config);
        // Null position = keep the current tab order (partial-PUT semantics).
        if (request.position() != null) {
            view.setPosition(request.position());
        }
        AppView saved = viewRepository.save(view);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "app_view_deleted", entityType = "AppView", entityId = "#viewId", entityName = "")
    public void deleteView(UUID appId, UUID viewId) {
        App app = getAppOrThrow(appId);
        AppView view = getViewOrThrow(appId, viewId);
        viewRepository.delete(view);
    }

    // ── definition validation ───────────────────────────────────────────

    /**
     * Type-vs-config validation at definition time: SELECT must carry a non-empty,
     * distinct, bounded option set; RELATION must carry an existing target app;
 * other types carry no config. FORMULA is rejected outright (deferred type).
     */
    private void validatePropertyDefinition(AppPropertyRequest request) {
        if (request.type() == PropertyType.FORMULA) {
            throw new BusinessException(ErrorCode.APP_PROPERTY_TYPE_INVALID,
                    "FORMULA properties are not supported yet (deferred — see ROADMAP 3.0.B)");
        }
        AppPropertyConfigDto config = request.config();
        switch (request.type()) {
            case SELECT -> {
                if (config == null || config.options() == null || config.options().isEmpty()) {
                    throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                            "SELECT properties require config.options with at least one option");
                }
                if (config.options().size() > MAX_SELECT_OPTIONS) {
                    throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                            "SELECT properties allow at most " + MAX_SELECT_OPTIONS + " options");
                }
                for (String option : config.options()) {
                    if (option == null || option.isBlank()) {
                        throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                                "SELECT options must be non-empty strings");
                    }
                    if (option.length() > MAX_OPTION_LENGTH) {
                        throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                                "SELECT options must be at most " + MAX_OPTION_LENGTH + " characters");
                    }
                }
                if (new LinkedHashSet<>(config.options()).size() != config.options().size()) {
                    throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                            "SELECT options must be distinct");
                }
                rejectUnexpectedTargetApp(request, "SELECT");
            }
            case RELATION -> {
                if (config == null || config.targetAppId() == null || config.targetAppId().isBlank()) {
                    throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                            "RELATION properties require config.targetAppId");
                }
                UUID targetAppId;
                try {
                    targetAppId = UUID.fromString(config.targetAppId());
                } catch (IllegalArgumentException ex) {
                    throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                            "config.targetAppId must be a UUID string");
                }
                if (!appRepository.existsById(targetAppId)) {
                    throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                            "config.targetAppId does not reference an existing app");
                }
                if (config.options() != null && !config.options().isEmpty()) {
                    throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                            "RELATION properties take no options");
                }
            }
            case TEXT, NUMBER, DATE, USER -> {
                if (config != null && ((config.options() != null && !config.options().isEmpty())
                        || (config.targetAppId() != null && !config.targetAppId().isBlank()))) {
                    throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                            request.type() + " properties take no config");
                }
            }
            case FORMULA -> throw new BusinessException(ErrorCode.APP_PROPERTY_TYPE_INVALID,
                    "FORMULA properties are not supported yet (deferred — see ROADMAP 3.0.B)");
        }
    }

    private void rejectUnexpectedTargetApp(AppPropertyRequest request, String type) {
        AppPropertyConfigDto config = request.config();
        if (config != null && config.targetAppId() != null && !config.targetAppId().isBlank()) {
            throw new BusinessException(ErrorCode.APP_PROPERTY_CONFIG_INVALID,
                    type + " properties take no targetAppId");
        }
    }

    private void applyPropertyRequest(AppProperty property, AppPropertyRequest request) {
        property.setName(request.name());
        property.setType(request.type());
        property.setRequired(request.required());
        // Null position = keep the current value (partial-PUT semantics on update;
        // create assigns max+1 in addProperty before this persists).
        if (request.position() != null) {
            property.setPosition(request.position());
        }
        property.setConfig(serializeConfig(request.config()));
    }

    /** 0-based append position for a new child (max+1; 0 when the app has none). */
    private int nextPosition(Integer max) {
        return max == null ? 0 : max + 1;
    }

    private String serializeConfig(AppPropertyConfigDto config) {
        if (config == null) {
            return null;
        }
        boolean empty = (config.options() == null || config.options().isEmpty())
                && (config.targetAppId() == null || config.targetAppId().isBlank());
        return empty ? null : objectMapper.writeValueAsString(config);
    }

    // ── lookups & mappers ───────────────────────────────────────────────

    private App getAppOrThrow(UUID id) {
        return appRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("App not found: " + id));
    }

    private AppProperty getPropertyOrThrow(UUID appId, UUID propertyId) {
        return propertyRepository.findByIdAndAppId(propertyId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));
    }

    private AppView getViewOrThrow(UUID appId, UUID viewId) {
        return viewRepository.findByIdAndAppId(viewId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("View not found: " + viewId));
    }

    private AppResponse toResponse(App app, String projectName) {
        return new AppResponse(app.getId(), app.getName(), app.getDescription(), app.getIcon(),
                app.getProjectId(), projectName, app.getCreatedDate(), app.getUpdatedAt());
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

    private AppPropertyResponse toResponse(AppProperty property) {
        return new AppPropertyResponse(property.getId(), property.getAppId(), property.getName(),
                property.getType(), parseConfig(property.getConfig()), property.isRequired(),
                property.getPosition());
    }

    private AppViewResponse toResponse(AppView view) {
        return new AppViewResponse(view.getId(), view.getAppId(), view.getName(), view.getType(),
                parseViewConfig(view.getConfig()), view.getPosition());
    }

    private AppPropertyConfigDto parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return null;
        }
        return objectMapper.readValue(config, AppPropertyConfigDto.class);
    }

    private AppViewConfigDto parseViewConfig(String config) {
        if (config == null || config.isBlank()) {
            return null;
        }
        return objectMapper.readValue(config, AppViewConfigDto.class);
    }
}
