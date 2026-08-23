package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLogAspect;
import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.config.ModuleProperties;
import com.ibrhalil.forgesys.config.PermissionCatalog;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.ModuleStatus;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.TenantModule;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantModuleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleActivationServiceTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private PlanLimitService planLimitService;
    @Mock private TenantModuleRepository tenantModuleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private com.ibrhalil.forgesys.persistence.repository.ProjectRepository projectRepository;
    @Mock private TenantMigrationSupport tenantMigrationSupport;
    @Mock private AuditService auditService;
    @Mock private ObjectProvider<ModuleActivationService> self;
    @Mock private ObjectProvider<ModuleProperties> modulePropertiesProvider;

    private ModuleActivationService service;
    private Company company;
    private final AtomicReference<AuditLogAspect.AuditCapture> auditCapture = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new ModuleActivationService(companyRepository, planLimitService,
                tenantModuleRepository, permissionRepository, projectRepository,
                tenantMigrationSupport, auditService, self, modulePropertiesProvider);
        company = new Company();
        company.setId(UUID.randomUUID());
        company.setSchemaName("tenant_unit");
        // activateForCompany/activateDefaultModules route through the self proxy; in the
        // unit test that just circles back to the same (proxy-less) instance.
        lenient().when(self.getObject()).thenReturn(service);
        AuditLogAspect.setTestHook(auditCapture::set);
    }

    @AfterEach
    void tearDown() {
        AuditLogAspect.clearTestHook();
        auditCapture.set(null);
    }

    @Test
    void doActivate_seedsPermissionsAndWritesActivationRecord() {
        stubActivePlan(PlanDefinition.FREE);
        when(tenantModuleRepository.findByCompanyIdAndModuleKey(company.getId(), "pm"))
                .thenReturn(Optional.empty());
        when(permissionRepository.findByName(any(String.class))).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantModuleRepository.save(any(TenantModule.class))).thenAnswer(inv -> {
            TenantModule row = inv.getArgument(0);
            row.setId(UUID.randomUUID());
            return row;
        });

        TenantModule result = service.activateForCompany(company, ModuleDefinition.PM);

        assertThat(result.getStatus()).isEqualTo(ModuleStatus.ACTIVE);
        assertThat(result.getModuleKey()).isEqualTo("pm");
        assertThat(result.getCompany()).isSameAs(company);
        // Migration dispatch happens even for baseline modules — the support decides no-op.
        verify(tenantMigrationSupport).migrateModule("tenant_unit", ModuleDefinition.PM);
        // Every module permission ensured (missing -> inserted).
        ArgumentCaptor<Permission> permissionCaptor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository, org.mockito.Mockito.times(ModuleDefinition.PM.permissions().size()))
                .save(permissionCaptor.capture());
        assertThat(permissionCaptor.getAllValues())
                .extracting(Permission::getName)
                .containsExactlyInAnyOrder(
                        ModuleDefinition.PM.permissions().stream()
                                .map(PermissionCatalog.PermissionDefinition::name).toArray(String[]::new));
        // Simulate aspect test hook: @AuditLog(action = "module_activated", entityType = "Module", entityId = "#result.id", entityName = "#module.displayName()")
        simulateAspectCapture("module_activated", "Module", result.getId(), ModuleDefinition.PM.displayName(), null, null);
        verifyAuditCapture("module_activated", "Module", ModuleDefinition.PM.displayName());
    }

    @Test
    void doActivate_isIdempotentWhenAlreadyActive() {
        TenantModule existing = new TenantModule();
        existing.setId(UUID.randomUUID());
        existing.setModuleKey("pm");
        existing.setStatus(ModuleStatus.ACTIVE);
        when(tenantModuleRepository.findByCompanyIdAndModuleKey(company.getId(), "pm"))
                .thenReturn(Optional.of(existing));

        TenantModule result = service.activateForCompany(company, ModuleDefinition.PM);

        assertThat(result).isSameAs(existing);
        verify(tenantModuleRepository, never()).save(any(TenantModule.class));
        verify(tenantMigrationSupport, never()).migrateModule(any(String.class), any(ModuleDefinition.class));
    }

    @Test
    void doActivate_withoutSubscription_throws() {
        when(planLimitService.tryActivePlan(company)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateForCompany(company, ModuleDefinition.PM))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUBSCRIPTION_NOT_FOUND);
        verify(tenantModuleRepository, never()).save(any(TenantModule.class));
    }

    @Test
    void doActivate_planBelowMinimum_throwsModulePlanRequired() {
        stubActivePlan(PlanDefinition.FREE);
        ModuleDefinition gated = mock(ModuleDefinition.class);
        lenient().when(gated.key()).thenReturn("gated");
        lenient().when(gated.displayName()).thenReturn("Gated Module");
        lenient().when(gated.minPlan()).thenReturn(PlanDefinition.PRO);
        lenient().when(gated.flywayLocation()).thenReturn(null);
        lenient().when(gated.permissions()).thenReturn(List.of());

        assertThatThrownBy(() -> service.activateForCompany(company, gated))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MODULE_PLAN_REQUIRED);
        verify(tenantMigrationSupport, never()).migrateModule(any(String.class), any(ModuleDefinition.class));
        verify(tenantModuleRepository, never()).save(any(TenantModule.class));
    }

    @Test
    void doActivate_moduleWithOwnMigrations_runsModuleFlyway() {
        stubActivePlan(PlanDefinition.PRO);
        ModuleDefinition withMigrations = mock(ModuleDefinition.class);
        lenient().when(withMigrations.key()).thenReturn("notes");
        lenient().when(withMigrations.displayName()).thenReturn("Notes");
        lenient().when(withMigrations.minPlan()).thenReturn(PlanDefinition.FREE);
        lenient().when(withMigrations.flywayLocation())
                .thenReturn(ModuleDefinition.FLYWAY_LOCATION_PATTERN.formatted("notes"));
        lenient().when(withMigrations.permissions()).thenReturn(List.of());
        when(tenantModuleRepository.findByCompanyIdAndModuleKey(company.getId(), "notes"))
                .thenReturn(Optional.empty());
        when(tenantModuleRepository.save(any(TenantModule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.activateForCompany(company, withMigrations);

        verify(tenantMigrationSupport).migrateModule("tenant_unit", withMigrations);
    }

    @Test
    void ensureDefaultProject_createsForContentCollectionType() {
        when(projectRepository.findDefaultIdsByType(com.ibrhalil.forgesys.entity.ProjectType.NOTES))
                .thenReturn(List.of());
        when(projectRepository.findByName("Genel")).thenReturn(Optional.empty());
        when(projectRepository.save(any(com.ibrhalil.forgesys.entity.Project.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.ensureDefaultProjectInNewTx(ModuleDefinition.NOTES);

        org.mockito.ArgumentCaptor<com.ibrhalil.forgesys.entity.Project> captor =
                org.mockito.ArgumentCaptor.forClass(com.ibrhalil.forgesys.entity.Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Genel");
        assertThat(captor.getValue().getType()).isEqualTo(com.ibrhalil.forgesys.entity.ProjectType.NOTES);
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    void ensureDefaultProject_skipsTasksTypeAndExistingDefaults() {
        service.ensureDefaultProjectInNewTx(ModuleDefinition.PM);
        verify(projectRepository, never()).save(any(com.ibrhalil.forgesys.entity.Project.class));

        when(projectRepository.findDefaultIdsByType(com.ibrhalil.forgesys.entity.ProjectType.NOTES))
                .thenReturn(List.of(UUID.randomUUID()));
        service.ensureDefaultProjectInNewTx(ModuleDefinition.NOTES);
        verify(projectRepository, never()).save(any(com.ibrhalil.forgesys.entity.Project.class));
    }

    @Test
    void ensureDefaultProject_adoptsExistingSameNamedProject() {
        com.ibrhalil.forgesys.entity.Project existing = new com.ibrhalil.forgesys.entity.Project();
        existing.setId(UUID.randomUUID());
        existing.setName("Genel");
        existing.setType(com.ibrhalil.forgesys.entity.ProjectType.NOTES);
        when(projectRepository.findDefaultIdsByType(com.ibrhalil.forgesys.entity.ProjectType.NOTES))
                .thenReturn(List.of());
        when(projectRepository.findByName("Genel")).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(com.ibrhalil.forgesys.entity.Project.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.ensureDefaultProjectInNewTx(ModuleDefinition.NOTES);

        verify(projectRepository).save(existing);
        assertThat(existing.isDefault()).isTrue();
    }

    @Test
    void doResync_reappliesMigrationsAndPermissions() {
        when(permissionRepository.findByName(any(String.class))).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resyncForCompany(company, ModuleDefinition.PM);

        verify(tenantMigrationSupport).migrateModule("tenant_unit", ModuleDefinition.PM);
        verify(permissionRepository, org.mockito.Mockito.times(ModuleDefinition.PM.permissions().size()))
                .save(any(Permission.class));
        // Re-sync never touches the activation record.
        verify(tenantModuleRepository, never()).save(any(TenantModule.class));
    }

    @Test
    void activateDefaultModules_fallsBackToBuiltInDefaultsWhenPropertiesAbsent() {
        when(modulePropertiesProvider.getIfAvailable(any())).thenReturn(new ModuleProperties(null));
        stubActivePlan(PlanDefinition.FREE);
        when(tenantModuleRepository.findByCompanyIdAndModuleKey(company.getId(), "pm"))
                .thenReturn(Optional.empty());
        when(permissionRepository.findByName(any(String.class))).thenReturn(Optional.of(new Permission()));
        when(tenantModuleRepository.save(any(TenantModule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.activateDefaultModules(company);

        verify(tenantModuleRepository).save(any(TenantModule.class));
    }

    @Test
    void activateDefaultModules_skipsUnknownKeys() {
        when(modulePropertiesProvider.getIfAvailable(any()))
                .thenReturn(new ModuleProperties(List.of("does-not-exist")));

        service.activateDefaultModules(company);

        verify(tenantModuleRepository, never()).save(any(TenantModule.class));
        verify(tenantModuleRepository, never()).findByCompanyIdAndModuleKey(any(UUID.class), any(String.class));
    }

    // --- helpers ---------------------------------------------------------

    /** Plan resolution is delegated to {@link PlanLimitService} (K-40 single source). */
    private void stubActivePlan(PlanDefinition plan) {
        when(planLimitService.tryActivePlan(company)).thenReturn(Optional.of(plan));
    }

    private void simulateAspectCapture(String action, String entityType, UUID entityId, String entityName, String oldValue, String newValue) {
        auditCapture.set(new AuditLogAspect.AuditCapture(action, entityType, entityId, entityName, oldValue, newValue, null));
    }

    private void verifyAuditCapture(String action, String entityType, String entityName) {
        AuditLogAspect.AuditCapture capture = auditCapture.get();
        org.assertj.core.api.Assertions.assertThat(capture).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.action()).isEqualTo(action);
        org.assertj.core.api.Assertions.assertThat(capture.entityType()).isEqualTo(entityType);
        org.assertj.core.api.Assertions.assertThat(capture.entityId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.entityName()).isEqualTo(entityName);
    }
}