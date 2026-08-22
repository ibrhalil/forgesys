package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.config.ModuleProperties;
import com.ibrhalil.forgesys.config.PermissionCatalog;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.ModuleStatus;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.entity.TenantModule;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantModuleRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for module activation (K-16 / Epic 3.0.A): plan gate, idempotency,
 * migration dispatch, permission seed and the default-module entry point. The plan
 * gate uses a Mockito-mocked {@link ModuleDefinition} (the real registry currently has
 * no plan-gated module — pm is FREE).
 */
@ExtendWith(MockitoExtension.class)
class ModuleActivationServiceTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private TenantModuleRepository tenantModuleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private TenantMigrationSupport tenantMigrationSupport;
    @Mock private AuditService auditService;
    @Mock private ObjectProvider<ModuleActivationService> self;
    @Mock private ObjectProvider<ModuleProperties> modulePropertiesProvider;

    private ModuleActivationService service;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new ModuleActivationService(companyRepository, subscriptionRepository,
                tenantModuleRepository, permissionRepository, tenantMigrationSupport,
                auditService, self, modulePropertiesProvider);
        company = new Company();
        company.setId(UUID.randomUUID());
        company.setSchemaName("tenant_unit");
        // activateForCompany/activateDefaultModules route through the self proxy; in the
        // unit test that just circles back to the same (proxy-less) instance.
        lenient().when(self.getObject()).thenReturn(service);
    }

    @Test
    void doActivate_seedsPermissionsAndWritesActivationRecord() {
        stubSubscription(plan("free", 0));
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
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("module_activated"),
                org.mockito.ArgumentMatchers.eq("Module"), any(UUID.class),
                org.mockito.ArgumentMatchers.eq(ModuleDefinition.PM.displayName()));
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
        when(subscriptionRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateForCompany(company, ModuleDefinition.PM))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUBSCRIPTION_NOT_FOUND);
        verify(tenantModuleRepository, never()).save(any(TenantModule.class));
    }

    @Test
    void doActivate_planBelowMinimum_throwsModulePlanRequired() {
        stubSubscription(plan("free", 0));
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
        stubSubscription(plan("pro", 1));
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
        stubSubscription(plan("free", 0));
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

    private void stubSubscription(Plan plan) {
        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByCompanyId(company.getId()))
                .thenReturn(Optional.of(subscription));
    }

    private Plan plan(String key, int rank) {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setKey(key);
        plan.setName(key);
        plan.setRank(rank);
        plan.setActive(true);
        return plan;
    }
}
