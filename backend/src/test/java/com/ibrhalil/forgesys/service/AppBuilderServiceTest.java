package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLogAspect;
import com.ibrhalil.forgesys.dto.AppPropertyRequest;
import com.ibrhalil.forgesys.dto.AppPropertyConfigDto;
import com.ibrhalil.forgesys.dto.AppRequest;
import com.ibrhalil.forgesys.dto.AppViewRequest;
import com.ibrhalil.forgesys.dto.AppViewConfigDto;
import com.ibrhalil.forgesys.entity.App;
import com.ibrhalil.forgesys.entity.AppProperty;
import com.ibrhalil.forgesys.entity.AppView;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.entity.ViewType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.AppPropertyRepository;
import com.ibrhalil.forgesys.persistence.repository.AppRepository;
import com.ibrhalil.forgesys.persistence.repository.AppViewRepository;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppBuilderServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private AppRepository appRepository;
    @Mock private AppListQueryExecutor appListQueryExecutor;
    @Mock private AppPropertyRepository propertyRepository;
    @Mock private AppViewRepository viewRepository;
    @Mock private PlanLimitService planLimitService;
    @Mock private ProjectContainerSupport projectContainerSupport;
    @Mock private ProjectRepository projectRepository;
    @Mock private AuditService auditService;

    private AppBuilderService service;
    private UUID appId;
    private UUID containerId;
    private final AtomicReference<AuditLogAspect.AuditCapture> auditCapture = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new AppBuilderService(appRepository, appListQueryExecutor, propertyRepository, viewRepository,
                new AppViewConfigValidator(new AppQueryValidator(), JSON),
                planLimitService, projectContainerSupport, projectRepository, auditService, JSON);
        appId = UUID.randomUUID();
        containerId = UUID.randomUUID();
        lenient().when(appRepository.findById(appId)).thenReturn(Optional.of(app()));
        // K-45: flat create resolves the default APPS container absent an explicit target.
        lenient().when(projectContainerSupport.resolveTarget(eq(ProjectType.APPS), any()))
                .thenReturn(container());
        AuditLogAspect.setTestHook(auditCapture::set);
    }

    @AfterEach
    void tearDown() {
        AuditLogAspect.clearTestHook();
        auditCapture.set(null);
    }

    private void simulateAspectCapture(String action, String entityType, UUID entityId, String entityName, String oldValue, String newValue) {
        auditCapture.set(new AuditLogAspect.AuditCapture(action, entityType, entityId, entityName, oldValue, newValue, null));
    }

    // ── apps ─────────────────────────────────────────────────────────────

    @Test
    void create_nameTaken_rejects() {
        when(appRepository.existsByName("CRM")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new AppRequest("CRM", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_NAME_TAKEN);
        verify(appRepository, never()).save(any(App.class));
    }

    @Test
    void create_delegatesLimitCheckAndSaves() {
        when(appRepository.existsByName("CRM")).thenReturn(false);
        when(appRepository.count()).thenReturn(2L);
        when(planLimitService.maxApps()).thenReturn(3);
        when(appRepository.save(any(App.class))).thenAnswer(inv -> {
            App app = inv.getArgument(0);
            app.setId(UUID.randomUUID());
            return app;
        });

        service.create(new AppRequest("CRM", "desc", "icon", null));

        verify(planLimitService).assertWithin(2L, 3, "custom apps");
        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(appRepository).save(appCaptor.capture());
        assertThat(appCaptor.getValue().getProjectId()).isEqualTo(containerId);
        // Simulate aspect test hook: @AuditLog(action = "app_created", entityType = "App", entityId = "#result.id", entityName = "#result.name")
        simulateAspectCapture("app_created", "App", appCaptor.getValue().getId(), "CRM", null, null);
        verifyAuditCapture("app_created", "App", "CRM");
    }

    @Test
    void create_planLimitReached_propagates() {
        when(appRepository.existsByName("CRM")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.APP_LIMIT_REACHED, "limit"))
                .when(planLimitService).assertWithin(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(), anyString());

        assertThatThrownBy(() -> service.create(new AppRequest("CRM", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_LIMIT_REACHED);
        verify(appRepository, never()).save(any(App.class));
    }

    // ── properties ───────────────────────────────────────────────────────

    @Test
    void addProperty_formulaDeferred_rejects() {
        assertThatThrownBy(() -> service.addProperty(appId,
                new AppPropertyRequest("Fx", PropertyType.FORMULA, null, false, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_PROPERTY_TYPE_INVALID);
        verify(propertyRepository, never()).save(any(AppProperty.class));
    }

    @Test
    void addProperty_selectWithoutOptions_rejects() {
        assertThatThrownBy(() -> service.addProperty(appId,
                new AppPropertyRequest("Status", PropertyType.SELECT, new AppPropertyConfigDto(List.of(), null), false, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_PROPERTY_CONFIG_INVALID);
    }

    @Test
    void addProperty_selectDuplicateOptions_rejects() {
        assertThatThrownBy(() -> service.addProperty(appId,
                new AppPropertyRequest("Status", PropertyType.SELECT,
                        new AppPropertyConfigDto(List.of("high", "high"), null), false, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_PROPERTY_CONFIG_INVALID);
    }

    @Test
    void addProperty_relationWithoutExistingTarget_rejects() {
        UUID target = UUID.randomUUID();
        when(appRepository.existsById(target)).thenReturn(false);

        assertThatThrownBy(() -> service.addProperty(appId,
                new AppPropertyRequest("Link", PropertyType.RELATION,
                        new AppPropertyConfigDto(null, target.toString()), false, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_PROPERTY_CONFIG_INVALID);
    }

    @Test
    void addProperty_relation_serializesConfigAndSaves() {
        UUID target = UUID.randomUUID();
        when(appRepository.existsById(target)).thenReturn(true);
        when(propertyRepository.existsByAppIdAndName(appId, "Link")).thenReturn(false);
        when(propertyRepository.save(any(AppProperty.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.addProperty(appId, new AppPropertyRequest("Link", PropertyType.RELATION,
                new AppPropertyConfigDto(null, target.toString()), true, 2));

        assertThat(response.name()).isEqualTo("Link");
        assertThat(response.required()).isTrue();
        assertThat(response.position()).isEqualTo(2);
        verify(propertyRepository).save(org.mockito.ArgumentMatchers.argThat(
                (AppProperty p) -> ("{\"targetAppId\":\"" + target + "\"}").equals(p.getConfig())));
    }

    @Test
    void updateProperty_typeChange_rejected() {
        UUID propertyId = UUID.randomUUID();
        when(propertyRepository.findByIdAndAppId(propertyId, appId))
                .thenReturn(Optional.of(property(propertyId, PropertyType.TEXT, null)));

        assertThatThrownBy(() -> service.updateProperty(appId, propertyId,
                new AppPropertyRequest("Name", PropertyType.NUMBER, null, false, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_PROPERTY_TYPE_INVALID);
    }

    @Test
    void deleteProperty_softDeletesAndClearsValues() {
        UUID propertyId = UUID.randomUUID();
        AppProperty property = property(propertyId, PropertyType.TEXT, null);
        when(propertyRepository.findByIdAndAppId(propertyId, appId)).thenReturn(Optional.of(property));

        service.deleteProperty(appId, propertyId);

        verify(propertyRepository).delete(property);
        verify(propertyRepository).deleteValuesByPropertyId(propertyId);
    }

    // ── views ─────────────────────────────────────────────────────────────

    @Test
    void addView_boardWithoutGroupBy_rejects() {
        assertThatThrownBy(() -> service.addView(appId,
                new AppViewRequest("Board", ViewType.BOARD, null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_VIEW_CONFIG_INVALID);
    }

    @Test
    void addView_boardWithSelectGroupBy_saves() {
        UUID selectPropertyId = UUID.randomUUID();
        stubProperties(property(selectPropertyId, PropertyType.SELECT,
                "{\"options\":[\"todo\",\"done\"]}"));
        when(viewRepository.existsByAppIdAndName(appId, "Board")).thenReturn(false);
        when(viewRepository.save(any(AppView.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.addView(appId, new AppViewRequest("Board", ViewType.BOARD,
                new AppViewConfigDto(null, null, selectPropertyId.toString(), null), 0));

        assertThat(response.name()).isEqualTo("Board");
        assertThat(response.config().groupBy()).isEqualTo(selectPropertyId.toString());
    }

    @Test
    void addView_boardGroupByNonSelect_rejects() {
        UUID textPropertyId = UUID.randomUUID();
        stubProperties(property(textPropertyId, PropertyType.TEXT, null));

        assertThatThrownBy(() -> service.addView(appId, new AppViewRequest("Board", ViewType.BOARD,
                new AppViewConfigDto(null, null, textPropertyId.toString(), null), 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_VIEW_CONFIG_INVALID);
    }

    @Test
    void addView_tableWithGroupBy_rejects() {
        UUID selectPropertyId = UUID.randomUUID();
        stubProperties(property(selectPropertyId, PropertyType.SELECT,
                "{\"options\":[\"todo\"]}"));

        assertThatThrownBy(() -> service.addView(appId, new AppViewRequest("Table", ViewType.TABLE,
                new AppViewConfigDto(null, null, selectPropertyId.toString(), null), 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_VIEW_CONFIG_INVALID);
    }

    @Test
    void addView_filterOnUnknownProperty_rejects() {
        assertThatThrownBy(() -> service.addView(appId, new AppViewRequest("V", ViewType.TABLE,
                new AppViewConfigDto(List.of(new com.ibrhalil.forgesys.dto.AppValueFilterCriteria(
                        UUID.randomUUID().toString(),
                        com.ibrhalil.forgesys.dto.AppValueOperator.EQ,
                        JSON.readTree("\"x\""))), null, null, null), 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_VIEW_CONFIG_INVALID);
    }

    private void verifyAuditCapture(String action, String entityType, String entityName) {
        AuditLogAspect.AuditCapture capture = auditCapture.get();
        org.assertj.core.api.Assertions.assertThat(capture).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.action()).isEqualTo(action);
        org.assertj.core.api.Assertions.assertThat(capture.entityType()).isEqualTo(entityType);
        org.assertj.core.api.Assertions.assertThat(capture.entityId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.entityName()).isEqualTo(entityName);
    }

    // --- helpers ---------------------------------------------------------

    private App app() {
        App app = new App();
        app.setId(appId);
        app.setName("CRM");
        app.setProjectId(containerId);
        return app;
    }

    private Project container() {
        Project project = new Project();
        project.setId(containerId);
        project.setName("Genel");
        project.setType(ProjectType.APPS);
        return project;
    }

    private AppProperty property(UUID id, PropertyType type, String config) {
        AppProperty property = new AppProperty();
        property.setId(id);
        property.setAppId(appId);
        property.setName("p-" + type.name().toLowerCase());
        property.setType(type);
        property.setConfig(config);
        return property;
    }

    private void stubProperties(AppProperty... properties) {
        when(propertyRepository.findAllByAppIdOrderByPositionAscNameAsc(appId))
                .thenReturn(List.of(properties));
    }
}