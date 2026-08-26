package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.CompanyModulesUpdateRequest;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.persistence.repository.AppRepository;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.NoteRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantModuleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformCompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private PlatformCompanyListQueryExecutor platformCompanyListQueryExecutor;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private TenantModuleRepository tenantModuleRepository;
    @Mock
    private PlanLimitService planLimitService;
    @Mock
    private ModuleActivationService moduleActivationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AppRepository appRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private PlatformAuditService platformAuditService;
    @Mock
    private PlatformUserRepository platformUserRepository;
    @Mock
    private ObjectProvider<PlatformCompanyService> self;

    private PlatformCompanyService platformCompanyService;

    @BeforeEach
    void setUp() {
        platformCompanyService = new PlatformCompanyService(companyRepository,
                platformCompanyListQueryExecutor, subscriptionRepository, planRepository,
                tenantModuleRepository, planLimitService, moduleActivationService,
                userRepository, projectRepository, appRepository, noteRepository,
                platformAuditService, platformUserRepository, self);
        // getReport routes the counting tx through the self proxy; in the unit
        // test that just circles back to the same (proxy-less) instance.
        lenient().when(self.getObject()).thenReturn(platformCompanyService);
    }

    /* ── updateStatus ── */

    @Test
    void updateStatusRecordsPlatformAuditAfterContextRestore() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.ACTIVE);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenReturn(company);

        platformCompanyService.updateStatus(id, CompanyStatus.SUSPENDED);

        assertThat(company.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
        // No SecurityContext in the unit test -> actor falls back to SYSTEM.
        verify(platformAuditService).record(isNull(), eq(PlatformAuditService.ACTOR_SYSTEM),
                eq(PlatformAuditService.ACTION_COMPANY_STATUS_UPDATED), eq("Company"), eq(id),
                eq("ACTIVE -> SUSPENDED"));
    }

    @Test
    void updateStatusRejectsIllegalTransitionAndDoesNotAudit() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.TERMINATED);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> platformCompanyService.updateStatus(id, CompanyStatus.ACTIVE))
                .isInstanceOf(BusinessException.class);

        verify(companyRepository, never()).save(any(Company.class));
        verify(platformAuditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    /* ── subscription ── */

    @Test
    void changePlanUpdatesPlanAndAudits() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.ACTIVE);
        Plan free = planFixture("free");
        Plan pro = planFixture("pro");
        Subscription subscription = subscriptionFixture(company, free);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));
        when(planRepository.findByKey("pro")).thenReturn(Optional.of(pro));
        when(subscriptionRepository.findByCompanyId(id)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenReturn(subscription);

        var response = platformCompanyService.changePlan(id, "pro");

        assertThat(response.planKey()).isEqualTo("pro");
        assertThat(subscription.getPlan()).isSameAs(pro);
        verify(platformAuditService).record(isNull(), eq(PlatformAuditService.ACTOR_SYSTEM),
                eq(PlatformAuditService.ACTION_TENANT_PLAN_CHANGED), eq("Company"), eq(id),
                eq("free -> pro"));
    }

    @Test
    void changePlanUnknownPlanKeyThrows404() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> platformCompanyService.changePlan(id, "ultra"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(com.ibrhalil.forgesys.exception.ErrorCode.PLAN_NOT_FOUND);
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void changePlanOnNonActiveCompanyThrows409() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> platformCompanyService.changePlan(id, "pro"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(com.ibrhalil.forgesys.exception.ErrorCode.COMPANY_NOT_ACTIVE);
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    /* ── modules ── */

    @Test
    void updateModulesDispatchesActivateAndDeactivate() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.ACTIVE);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));
        when(tenantModuleRepository.findByCompanyId(id)).thenReturn(List.of());
        when(planLimitService.tryActivePlan(company))
                .thenReturn(Optional.of(com.ibrhalil.forgesys.config.PlanDefinition.PRO));

        platformCompanyService.updateModules(id, new CompanyModulesUpdateRequest(List.of(
                new CompanyModulesUpdateRequest.ModuleActivation("pm", true),
                new CompanyModulesUpdateRequest.ModuleActivation("notes", false))));

        verify(moduleActivationService).activateForCompany(company, com.ibrhalil.forgesys.config.ModuleDefinition.PM);
        verify(moduleActivationService).deactivateForCompany(company, com.ibrhalil.forgesys.config.ModuleDefinition.NOTES);
        verify(platformAuditService).record(isNull(), eq(PlatformAuditService.ACTOR_SYSTEM),
                eq(PlatformAuditService.ACTION_TENANT_MODULE_ACTIVATED), eq("Company"), eq(id), eq("module=pm"));
        verify(platformAuditService).record(isNull(), eq(PlatformAuditService.ACTOR_SYSTEM),
                eq(PlatformAuditService.ACTION_TENANT_MODULE_DEACTIVATED), eq("Company"), eq(id), eq("module=notes"));
    }

    @Test
    void updateModulesUnknownKeyThrows404() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.ACTIVE);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> platformCompanyService.updateModules(id,
                new CompanyModulesUpdateRequest(List.of(
                        new CompanyModulesUpdateRequest.ModuleActivation("does-not-exist", true)))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(com.ibrhalil.forgesys.exception.ErrorCode.MODULE_NOT_FOUND);
        verify(moduleActivationService, never()).activateForCompany(any(), any());
    }

    @Test
    void updateModulesOnSuspendedCompanyThrows409() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.TERMINATED);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> platformCompanyService.updateModules(id,
                new CompanyModulesUpdateRequest(List.of(
                        new CompanyModulesUpdateRequest.ModuleActivation("pm", true)))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(com.ibrhalil.forgesys.exception.ErrorCode.COMPANY_NOT_ACTIVE);
    }

    /* ── report ── */

    @Test
    void getReportCountsInsideTenantWindowWithoutHydration() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.ACTIVE);
        company.setSchemaName("tenant_acme");
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));
        when(userRepository.count()).thenReturn(7L);
        when(projectRepository.count()).thenReturn(5L);
        when(appRepository.count()).thenReturn(3L);
        when(noteRepository.count()).thenReturn(11L);

        var report = platformCompanyService.getReport(id);

        assertThat(report.companyId()).isEqualTo(id);
        assertThat(report.userCount()).isEqualTo(7L);
        assertThat(report.projectCount()).isEqualTo(5L);
        assertThat(report.appCount()).isEqualTo(3L);
        assertThat(report.noteCount()).isEqualTo(11L);
    }

    /* ── fixtures ── */

    private Company companyFixture(UUID id, String name, CompanyStatus status) {
        Company company = new Company();
        company.setId(id);
        company.setName(name);
        company.setStatus(status);
        company.setSchemaName("tenant_" + name.toLowerCase());
        return company;
    }

    private Plan planFixture(String key) {
        Plan plan = new Plan();
        plan.setKey(key);
        plan.setName(key.toUpperCase());
        plan.setRank(1);
        plan.setActive(true);
        return plan;
    }

    private Subscription subscriptionFixture(Company company, Plan plan) {
        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(java.time.OffsetDateTime.now());
        return subscription;
    }
}
