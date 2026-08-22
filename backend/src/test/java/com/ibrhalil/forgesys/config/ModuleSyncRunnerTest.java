package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.ModuleStatus;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.entity.TenantModule;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantModuleRepository;
import com.ibrhalil.forgesys.service.ModuleActivationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the startup module sync (K-16 / Epic 3.0.A): FREE subscription
 * backfill, default-module activation and per-module re-sync of already-ACTIVE modules.
 */
@ExtendWith(MockitoExtension.class)
class ModuleSyncRunnerTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private PlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private TenantModuleRepository tenantModuleRepository;
    @Mock private ModuleActivationService moduleActivationService;
    @Mock private ObjectProvider<ModuleSyncRunner> self;

    private ModuleSyncRunner runner;
    private Company company;
    private Plan freePlan;

    @BeforeEach
    void setUp() {
        runner = new ModuleSyncRunner(companyRepository, planRepository, subscriptionRepository,
                tenantModuleRepository, moduleActivationService, self);
        company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Acme");
        company.setSubdomain("acme");
        company.setSchemaName("tenant_acme");
        company.setStatus(CompanyStatus.ACTIVE);
        freePlan = new Plan();
        freePlan.setId(UUID.randomUUID());
        freePlan.setKey(PlanDefinition.FREE.key());
        freePlan.setName("Free");
        freePlan.setRank(0);
        lenient().when(self.getObject()).thenReturn(runner);
    }

    @Test
    void syncCompany_backfillsFreeSubscriptionForLegacyTenant() {
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(subscriptionRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());
        when(planRepository.findByKey(PlanDefinition.FREE.key())).thenReturn(Optional.of(freePlan));

        runner.syncCompany(company.getId());

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getCompany()).isSameAs(company);
        assertThat(captor.getValue().getPlan()).isSameAs(freePlan);
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(moduleActivationService).activateDefaultModules(company);
    }

    @Test
    void syncCompany_skipsBackfillWhenSubscriptionExists() {
        Subscription existing = new Subscription();
        existing.setCompany(company);
        existing.setPlan(freePlan);
        existing.setStatus(SubscriptionStatus.ACTIVE);
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(subscriptionRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(existing));

        runner.syncCompany(company.getId());

        verify(subscriptionRepository, never()).save(any(Subscription.class));
        verify(moduleActivationService).activateDefaultModules(company);
    }

    @Test
    void syncCompany_resyncsActivatedModulesButSkipsUnknownKeys() {
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(subscriptionRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());
        when(planRepository.findByKey(PlanDefinition.FREE.key())).thenReturn(Optional.of(freePlan));
        TenantModule pmRow = new TenantModule();
        pmRow.setCompany(company);
        pmRow.setModuleKey("pm");
        pmRow.setStatus(ModuleStatus.ACTIVE);
        TenantModule ghostRow = new TenantModule();
        ghostRow.setCompany(company);
        ghostRow.setModuleKey("removed-from-registry");
        ghostRow.setStatus(ModuleStatus.ACTIVE);
        when(tenantModuleRepository.findByCompanyId(company.getId())).thenReturn(List.of(pmRow, ghostRow));

        runner.syncCompany(company.getId());

        // Known key re-synced; unknown key logged + skipped (only one resync total).
        verify(moduleActivationService).resyncForCompany(company, ModuleDefinition.PM);
        verify(moduleActivationService, org.mockito.Mockito.times(1))
                .resyncForCompany(any(Company.class), any(ModuleDefinition.class));
    }

    @Test
    void run_isolatesFailuresPerCompany() {
        Company failing = new Company();
        failing.setId(UUID.randomUUID());
        failing.setSchemaName("tenant_bad");
        when(companyRepository.findAllTenantSchemas())
                .thenReturn(List.of(view(failing.getId()), view(company.getId())));
        when(companyRepository.findById(failing.getId()))
                .thenThrow(new RuntimeException("sync exploded"));
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(subscriptionRepository.findByCompanyId(any(UUID.class))).thenReturn(Optional.empty());
        when(planRepository.findByKey(PlanDefinition.FREE.key())).thenReturn(Optional.of(freePlan));

        runner.run(null);

        // The second company was still synced after the first one blew up.
        verify(moduleActivationService).activateDefaultModules(company);
    }

    private CompanyRepository.TenantSchemaView view(UUID id) {
        return new CompanyRepository.TenantSchemaView() {
            @Override public UUID getId() { return id; }
            @Override public String getSchemaName() { return "tenant_" + id; }
            @Override public CompanyStatus getStatus() { return CompanyStatus.ACTIVE; }
        };
    }
}
