package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AppPropertyRequest;
import com.ibrhalil.forgesys.dto.AppPropertyConfigDto;
import com.ibrhalil.forgesys.dto.AppRequest;
import com.ibrhalil.forgesys.dto.AppViewRequest;
import com.ibrhalil.forgesys.dto.AppViewConfigDto;
import com.ibrhalil.forgesys.entity.App;
import com.ibrhalil.forgesys.entity.AppProperty;
import com.ibrhalil.forgesys.entity.AppView;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.entity.ViewType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.AppPropertyRepository;
import com.ibrhalil.forgesys.persistence.repository.AppRepository;
import com.ibrhalil.forgesys.persistence.repository.AppViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

/**
 * Unit tests for the app-definition service (K-15 / Epic 3.0.B): plan-limit delegation,
 * TOCTOU name checks, property type/config rules (FORMULA deferral, SELECT/RELATION
 * configs) and view-config anchors (BOARD/CALENDAR). Validators are real instances;
 * repositories and the audit are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AppBuilderServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private AppRepository appRepository;
    @Mock private AppPropertyRepository propertyRepository;
    @Mock private AppViewRepository viewRepository;
    @Mock private PlanLimitService planLimitService;
    @Mock private AuditService auditService;

    private AppBuilderService service;
    private UUID appId;

    @BeforeEach
    void setUp() {
        service = new AppBuilderService(appRepository, propertyRepository, viewRepository,
                new AppViewConfigValidator(new AppQueryValidator(), JSON),
                planLimitService, auditService, JSON);
        appId = UUID.randomUUID();
        lenient().when(appRepository.findById(appId)).thenReturn(Optional.of(app()));
    }

    // ── apps ─────────────────────────────────────────────────────────────

    @Test
    void create_nameTaken_rejects() {
        when(appRepository.existsByName("CRM")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new AppRequest("CRM", null, null)))
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

        service.create(new AppRequest("CRM", "desc", "icon"));

        verify(planLimitService).assertWithin(2L, 3, "custom apps");
        verify(appRepository).save(any(App.class));
        verify(auditService).record(eq("app_created"), eq("App"), any(UUID.class), eq("CRM"));
    }

    @Test
    void create_planLimitReached_propagates() {
        when(appRepository.existsByName("CRM")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.APP_LIMIT_REACHED, "limit"))
                .when(planLimitService).assertWithin(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(), anyString());

        assertThatThrownBy(() -> service.create(new AppRequest("CRM", null, null)))
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

    // ── views ────────────────────────────────────────────────────────────

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

    // --- helpers ---------------------------------------------------------

    private App app() {
        App app = new App();
        app.setId(appId);
        app.setName("CRM");
        return app;
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
