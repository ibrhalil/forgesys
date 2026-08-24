package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.SampleDataProperties;
import com.ibrhalil.forgesys.dto.AppPropertyRequest;
import com.ibrhalil.forgesys.dto.AppPropertyResponse;
import com.ibrhalil.forgesys.dto.AppRecordRequest;
import com.ibrhalil.forgesys.dto.AppRequest;
import com.ibrhalil.forgesys.dto.AppResponse;
import com.ibrhalil.forgesys.dto.AppViewRequest;
import com.ibrhalil.forgesys.dto.NoteCategoryRequest;
import com.ibrhalil.forgesys.dto.NoteCategoryResponse;
import com.ibrhalil.forgesys.dto.NoteRequest;
import com.ibrhalil.forgesys.dto.ProjectRequest;
import com.ibrhalil.forgesys.dto.ProjectResponse;
import com.ibrhalil.forgesys.dto.TaskRequest;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.entity.ViewType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the K-47 sample-data seed: the config gate, the fail-safe contract
 * (a seed failure must never escape to provisioning) and the seeded content shape
 * (pm/notes/apps call counts and arguments). Pure Mockito — no H2, no Spring context;
 * {@code seedInNewTx} runs directly through the self stub (the REQUIRES_NEW proxy
 * boundary is a Spring concern, verified by the ITs).
 */
@ExtendWith(MockitoExtension.class)
class TenantSampleDataServiceTest {

    private static final String SCHEMA_NAME = "tenant_acme";
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID APP_ID = UUID.randomUUID();
    private static final UUID GUIDES_ID = UUID.randomUUID();
    private static final UUID IDEAS_ID = UUID.randomUUID();
    private static final UUID NAME_PROP = UUID.randomUUID();
    private static final UUID STAGE_PROP = UUID.randomUUID();
    private static final UUID DATE_PROP = UUID.randomUUID();

    @Mock private ProjectService projectService;
    @Mock private TaskService taskService;
    @Mock private NoteCategoryService noteCategoryService;
    @Mock private NoteService noteService;
    @Mock private AppBuilderService appBuilderService;
    @Mock private AppRecordService appRecordService;
    @Mock private ObjectProvider<TenantSampleDataService> self;

    private TenantSampleDataService service;

    @BeforeEach
    void setUp() {
        service = new TenantSampleDataService(projectService, taskService, noteCategoryService,
                noteService, appBuilderService, appRecordService, new SampleDataProperties(true), self);
        // Same (proxy-less) instance — seedInNewTx runs inline like in production tests.
        lenient().when(self.getObject()).thenReturn(service);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void disabled_neverTouchesAnyService() {
        TenantSampleDataService gated = new TenantSampleDataService(projectService, taskService,
                noteCategoryService, noteService, appBuilderService, appRecordService,
                new SampleDataProperties(false), self);

        gated.seedForCompany(company(), ADMIN_ID);

        verifyNoInteractions(projectService, taskService, noteCategoryService, noteService,
                appBuilderService, appRecordService);
        verify(self, never()).getObject();
    }

    @Test
    void seedFailure_isSwallowed_provisioningContinues() {
        when(projectService.create(any(ProjectRequest.class))).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> service.seedForCompany(company(), ADMIN_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void seed_seedsEveryModuleInsideTheTenantContext() {
        AtomicReference<String> tenantAtSeed = new AtomicReference<>();
        stubContentChain(tenantAtSeed);

        service.seedForCompany(company(), ADMIN_ID);

        // The seed ran inside the company's tenant context and restored it afterward.
        assertThat(tenantAtSeed.get()).isEqualTo(SCHEMA_NAME);
        assertThat(TenantContext.getCurrentTenant()).isEmpty();

        // pm: exactly one TASKS project, four guided tasks assigned to the admin.
        ArgumentCaptor<ProjectRequest> projectCaptor = ArgumentCaptor.forClass(ProjectRequest.class);
        verify(projectService).create(projectCaptor.capture());
        assertThat(projectCaptor.getValue().name()).isEqualTo("Getting Started");
        assertThat(projectCaptor.getValue().type()).isEqualTo(ProjectType.TASKS);

        ArgumentCaptor<TaskRequest> taskCaptor = ArgumentCaptor.forClass(TaskRequest.class);
        verify(taskService, times(4)).create(eq(PROJECT_ID), taskCaptor.capture());
        assertThat(taskCaptor.getAllValues())
                .extracting(TaskRequest::assigneeId)
                .containsOnly(ADMIN_ID);
        LocalDate today = LocalDate.now();
        assertThat(taskCaptor.getAllValues())
                .extracting(TaskRequest::dueDate)
                .containsExactly(today.plusDays(2), today.plusDays(3), today.plusDays(4), today.plusDays(5));

        // notes: two categories, two notes — both filed under "Guides", the first pinned.
        verify(noteCategoryService, times(2)).create(any(NoteCategoryRequest.class));
        ArgumentCaptor<NoteRequest> noteCaptor = ArgumentCaptor.forClass(NoteRequest.class);
        verify(noteService, times(2)).create(noteCaptor.capture());
        assertThat(noteCaptor.getAllValues())
                .extracting(NoteRequest::categoryId)
                .containsOnly(GUIDES_ID);
        assertThat(noteCaptor.getAllValues().get(0).pinned()).isTrue();

        // apps: one app, three properties (ordered/select/date), two views, four records.
        verify(appBuilderService).create(any(AppRequest.class));
        ArgumentCaptor<AppPropertyRequest> propCaptor = ArgumentCaptor.forClass(AppPropertyRequest.class);
        verify(appBuilderService, times(3)).addProperty(eq(APP_ID), propCaptor.capture());
        List<AppPropertyRequest> props = propCaptor.getAllValues();
        assertThat(props).extracting(AppPropertyRequest::name)
                .containsExactly("Name", "Stage", "Launch date");
        assertThat(props).extracting(AppPropertyRequest::type)
                .containsExactly(PropertyType.TEXT, PropertyType.SELECT, PropertyType.DATE);
        assertThat(props).extracting(AppPropertyRequest::required)
                .containsExactly(true, false, false);
        assertThat(props).extracting(AppPropertyRequest::position)
                .containsExactly(0, 1, 2);
        assertThat(props.get(1).config().options())
                .containsExactly("Discovery", "In Progress", "Launched");

        ArgumentCaptor<AppViewRequest> viewCaptor = ArgumentCaptor.forClass(AppViewRequest.class);
        verify(appBuilderService, times(2)).addView(eq(APP_ID), viewCaptor.capture());
        assertThat(viewCaptor.getAllValues()).extracting(AppViewRequest::type)
                .containsExactly(ViewType.TABLE, ViewType.BOARD);
        // The BOARD anchor groups by the Stage SELECT property id (validator contract).
        assertThat(viewCaptor.getAllValues().get(1).config().groupBy())
                .isEqualTo(STAGE_PROP.toString());

        ArgumentCaptor<AppRecordRequest> recordCaptor = ArgumentCaptor.forClass(AppRecordRequest.class);
        verify(appRecordService, times(4)).create(eq(APP_ID), recordCaptor.capture());
        // Every record covers the required Name; Stage is spread across three options
        // with one record deliberately empty (the Board's empty bucket).
        assertThat(recordCaptor.getAllValues())
                .allSatisfy(record -> assertThat(record.values()).containsKey(NAME_PROP.toString()));
        assertThat(recordCaptor.getAllValues().stream()
                .filter(record -> record.values().containsKey(STAGE_PROP.toString()))
                .count()).isEqualTo(3);
    }

    // --- helpers ---------------------------------------------------------

    private Company company() {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Acme");
        company.setSubdomain("acme");
        company.setSchemaName(SCHEMA_NAME);
        company.setStatus(CompanyStatus.ACTIVE);
        return company;
    }

    private void stubContentChain(AtomicReference<String> tenantAtSeed) {
        when(projectService.create(any(ProjectRequest.class))).thenAnswer(inv -> {
            tenantAtSeed.set(TenantContext.getCurrentTenant().orElse(null));
            return new ProjectResponse(PROJECT_ID, "Getting Started", null, ProjectType.TASKS, null, false);
        });
        when(noteCategoryService.create(any(NoteCategoryRequest.class))).thenAnswer(inv -> {
            NoteCategoryRequest request = inv.getArgument(0);
            UUID id = "Guides".equals(request.name()) ? GUIDES_ID : IDEAS_ID;
            return new NoteCategoryResponse(id, request.name(), null, null);
        });
        when(appBuilderService.create(any(AppRequest.class)))
                .thenReturn(new AppResponse(APP_ID, "Team Tracker", null, null, null, null, null, null));
        when(appBuilderService.addProperty(eq(APP_ID), any(AppPropertyRequest.class))).thenAnswer(inv -> {
            AppPropertyRequest request = inv.getArgument(1);
            return new AppPropertyResponse(propertyId(request.name()), APP_ID, request.name(),
                    request.type(), request.config(), request.required(), request.position());
        });
    }

    private UUID propertyId(String name) {
        return switch (name) {
            case "Name" -> NAME_PROP;
            case "Stage" -> STAGE_PROP;
            case "Launch date" -> DATE_PROP;
            default -> throw new IllegalArgumentException("Unexpected sample property: " + name);
        };
    }
}
