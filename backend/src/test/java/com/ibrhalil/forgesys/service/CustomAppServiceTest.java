package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLogAspect;
import com.ibrhalil.forgesys.dto.CustomAppPropertyRequest;
import com.ibrhalil.forgesys.dto.CustomAppPropertyConfigDto;
import com.ibrhalil.forgesys.dto.CustomAppRequest;
import com.ibrhalil.forgesys.dto.CustomAppViewRequest;
import com.ibrhalil.forgesys.dto.CustomAppViewConfigDto;
import com.ibrhalil.forgesys.entity.CustomApp;
import com.ibrhalil.forgesys.entity.CustomAppProperty;
import com.ibrhalil.forgesys.entity.CustomAppView;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.entity.ViewType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CustomAppPropertyRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppViewRepository;
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
class CustomAppServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private CustomAppRepository customAppRepository;
    @Mock private CustomAppListQueryExecutor customAppListQueryExecutor;
    @Mock private CustomAppPropertyRepository propertyRepository;
    @Mock private CustomAppViewRepository viewRepository;
    @Mock private PlanLimitService planLimitService;
    @Mock private ProjectContainerSupport projectContainerSupport;
    @Mock private ProjectRepository projectRepository;
    @Mock private AuditService auditService;

    private CustomAppService service;
    private UUID customAppId;
    private UUID containerId;
    private final AtomicReference<AuditLogAspect.AuditCapture> auditCapture = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new CustomAppService(customAppRepository, customAppListQueryExecutor, propertyRepository, viewRepository,
                new CustomAppViewConfigValidator(new CustomAppQueryValidator(), JSON),
                planLimitService, projectContainerSupport, projectRepository, auditService, JSON);
        customAppId = UUID.randomUUID();
        containerId = UUID.randomUUID();
        lenient().when(customAppRepository.findById(customAppId)).thenReturn(Optional.of(app()));
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
        when(customAppRepository.existsByName("CRM")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CustomAppRequest("CRM", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_NAME_TAKEN);
        verify(customAppRepository, never()).save(any(CustomApp.class));
    }

    @Test
    void create_delegatesLimitCheckAndSaves() {
        when(customAppRepository.existsByName("CRM")).thenReturn(false);
        when(customAppRepository.count()).thenReturn(2L);
        when(planLimitService.maxCustomApps()).thenReturn(3);
        when(customAppRepository.save(any(CustomApp.class))).thenAnswer(inv -> {
            CustomApp app = inv.getArgument(0);
            app.setId(UUID.randomUUID());
            return app;
        });

        service.create(new CustomAppRequest("CRM", "desc", "icon", null));

        verify(planLimitService).assertWithin(2L, 3, "custom apps");
        ArgumentCaptor<CustomApp> appCaptor = ArgumentCaptor.forClass(CustomApp.class);
        verify(customAppRepository).save(appCaptor.capture());
        assertThat(appCaptor.getValue().getProjectId()).isEqualTo(containerId);
        // Simulate aspect test hook: @AuditLog(action = "custom_app_created", entityType = "CustomApp", entityId = "#result.id", entityName = "#result.name")
        simulateAspectCapture("custom_app_created", "CustomApp", appCaptor.getValue().getId(), "CRM", null, null);
        verifyAuditCapture("custom_app_created", "CustomApp", "CRM");
    }

    @Test
    void create_planLimitReached_propagates() {
        when(customAppRepository.existsByName("CRM")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.CUSTOM_APP_LIMIT_REACHED, "limit"))
                .when(planLimitService).assertWithin(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(), anyString());

        assertThatThrownBy(() -> service.create(new CustomAppRequest("CRM", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_LIMIT_REACHED);
        verify(customAppRepository, never()).save(any(CustomApp.class));
    }

    // ── properties ───────────────────────────────────────────────────────

    @Test
    void addProperty_formulaDeferred_rejects() {
        assertThatThrownBy(() -> service.addProperty(customAppId,
                new CustomAppPropertyRequest("Fx", PropertyType.FORMULA, null, false, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_PROPERTY_TYPE_INVALID);
        verify(propertyRepository, never()).save(any(CustomAppProperty.class));
    }

    @Test
    void addProperty_selectWithoutOptions_rejects() {
        assertThatThrownBy(() -> service.addProperty(customAppId,
                new CustomAppPropertyRequest("Status", PropertyType.SELECT, new CustomAppPropertyConfigDto(List.of(), null), false, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID);
    }

    @Test
    void addProperty_selectDuplicateOptions_rejects() {
        assertThatThrownBy(() -> service.addProperty(customAppId,
                new CustomAppPropertyRequest("Status", PropertyType.SELECT,
                        new CustomAppPropertyConfigDto(List.of("high", "high"), null), false, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID);
    }

    @Test
    void addProperty_relationWithoutExistingTarget_rejects() {
        UUID target = UUID.randomUUID();
        when(customAppRepository.existsById(target)).thenReturn(false);

        assertThatThrownBy(() -> service.addProperty(customAppId,
                new CustomAppPropertyRequest("Link", PropertyType.RELATION,
                        new CustomAppPropertyConfigDto(null, target.toString()), false, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID);
    }

    @Test
    void addProperty_relation_serializesConfigAndSaves() {
        UUID target = UUID.randomUUID();
        when(customAppRepository.existsById(target)).thenReturn(true);
        when(propertyRepository.existsByCustomAppIdAndName(customAppId, "Link")).thenReturn(false);
        when(propertyRepository.save(any(CustomAppProperty.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.addProperty(customAppId, new CustomAppPropertyRequest("Link", PropertyType.RELATION,
                new CustomAppPropertyConfigDto(null, target.toString()), true, 2));

        assertThat(response.name()).isEqualTo("Link");
        assertThat(response.required()).isTrue();
        assertThat(response.position()).isEqualTo(2);
        verify(propertyRepository).save(org.mockito.ArgumentMatchers.argThat(
                (CustomAppProperty p) -> ("{\"targetCustomAppId\":\"" + target + "\"}").equals(p.getConfig())));
    }

    @Test
    void updateProperty_typeChange_rejected() {
        UUID propertyId = UUID.randomUUID();
        when(propertyRepository.findByIdAndCustomAppId(propertyId, customAppId))
                .thenReturn(Optional.of(property(propertyId, PropertyType.TEXT, null)));

        assertThatThrownBy(() -> service.updateProperty(customAppId, propertyId,
                new CustomAppPropertyRequest("Name", PropertyType.NUMBER, null, false, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_PROPERTY_TYPE_INVALID);
    }

    @Test
    void deleteProperty_softDeletesAndClearsValues() {
        UUID propertyId = UUID.randomUUID();
        CustomAppProperty property = property(propertyId, PropertyType.TEXT, null);
        when(propertyRepository.findByIdAndCustomAppId(propertyId, customAppId)).thenReturn(Optional.of(property));

        service.deleteProperty(customAppId, propertyId);

        verify(propertyRepository).delete(property);
        verify(propertyRepository).deleteValuesByPropertyId(propertyId);
    }

    // ── views ─────────────────────────────────────────────────────────────

    @Test
    void addView_boardWithoutGroupBy_rejects() {
        assertThatThrownBy(() -> service.addView(customAppId,
                new CustomAppViewRequest("Board", ViewType.BOARD, null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_VIEW_CONFIG_INVALID);
    }

    @Test
    void addView_boardWithSelectGroupBy_saves() {
        UUID selectPropertyId = UUID.randomUUID();
        stubProperties(property(selectPropertyId, PropertyType.SELECT,
                "{\"options\":[\"todo\",\"done\"]}"));
        when(viewRepository.existsByCustomAppIdAndName(customAppId, "Board")).thenReturn(false);
        when(viewRepository.save(any(CustomAppView.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.addView(customAppId, new CustomAppViewRequest("Board", ViewType.BOARD,
                new CustomAppViewConfigDto(null, null, selectPropertyId.toString(), null), 0));

        assertThat(response.name()).isEqualTo("Board");
        assertThat(response.config().groupBy()).isEqualTo(selectPropertyId.toString());
    }

    @Test
    void addView_boardGroupByNonSelect_rejects() {
        UUID textPropertyId = UUID.randomUUID();
        stubProperties(property(textPropertyId, PropertyType.TEXT, null));

        assertThatThrownBy(() -> service.addView(customAppId, new CustomAppViewRequest("Board", ViewType.BOARD,
                new CustomAppViewConfigDto(null, null, textPropertyId.toString(), null), 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_VIEW_CONFIG_INVALID);
    }

    @Test
    void addView_tableWithGroupBy_rejects() {
        UUID selectPropertyId = UUID.randomUUID();
        stubProperties(property(selectPropertyId, PropertyType.SELECT,
                "{\"options\":[\"todo\"]}"));

        assertThatThrownBy(() -> service.addView(customAppId, new CustomAppViewRequest("Table", ViewType.TABLE,
                new CustomAppViewConfigDto(null, null, selectPropertyId.toString(), null), 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_VIEW_CONFIG_INVALID);
    }

    @Test
    void addView_filterOnUnknownProperty_rejects() {
        assertThatThrownBy(() -> service.addView(customAppId, new CustomAppViewRequest("V", ViewType.TABLE,
                new CustomAppViewConfigDto(List.of(new com.ibrhalil.forgesys.dto.CustomAppValueFilterCriteria(
                        UUID.randomUUID().toString(),
                        com.ibrhalil.forgesys.dto.CustomAppValueOperator.EQ,
                        JSON.readTree("\"x\""))), null, null, null), 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_VIEW_CONFIG_INVALID);
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

    private CustomApp app() {
        CustomApp app = new CustomApp();
        app.setId(customAppId);
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

    private CustomAppProperty property(UUID id, PropertyType type, String config) {
        CustomAppProperty property = new CustomAppProperty();
        property.setId(id);
        property.setCustomAppId(customAppId);
        property.setName("p-" + type.name().toLowerCase());
        property.setType(type);
        property.setConfig(config);
        return property;
    }

    private void stubProperties(CustomAppProperty... properties) {
        when(propertyRepository.findAllByCustomAppIdOrderByPositionAscNameAsc(customAppId))
                .thenReturn(List.of(properties));
    }
}