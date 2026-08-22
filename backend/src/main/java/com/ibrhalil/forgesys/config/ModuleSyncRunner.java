package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.Company;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Startup module sync (K-16 / Epic 3.0.A). Per company (try/catch isolation, mirroring
 * {@code TenantMigrationRunner}/{@code RbacSeeder}):
 * <ol>
 *   <li><strong>Subscription backfill</strong> — tenants provisioned before the module
 *       system get a FREE subscription (preserves the pre-3.0.A behavior where every
 *       tenant had every module).</li>
 *   <li><strong>Default modules</strong> — {@code forgesys.modules.default-keys} ensured
 *       ACTIVE (idempotent activation; pm backfills every existing tenant).</li>
 *   <li><strong>Re-sync</strong> — every already-ACTIVE module gets its migrations +
 *       permission seed re-applied, so newly shipped module migrations/permissions
 *       propagate to existing tenants.</li>
 * </ol>
 *
 * <p>Runs after {@link PlanSyncRunner} ({@code @Order(0)} — plan rows must exist for
 * the FREE backfill). Disabled in the {@code test} profile.
 */
@Slf4j
@Component
@Profile("!test")
@EnableConfigurationProperties(ModuleProperties.class)
@RequiredArgsConstructor
public class ModuleSyncRunner implements ApplicationRunner {

    private final CompanyRepository companyRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantModuleRepository tenantModuleRepository;
    private final ModuleActivationService moduleActivationService;
    private final ObjectProvider<ModuleSyncRunner> self;

    @Override
    public void run(ApplicationArguments args) {
        for (CompanyRepository.TenantSchemaView tenant : companyRepository.findAllTenantSchemas()) {
            try {
                self.getObject().syncCompany(tenant.getId());
            } catch (Exception e) {
                log.error("Module sync failed for company: {}", tenant.getId(), e);
            }
        }
    }

    @Transactional
    public void syncCompany(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return;
        }
        ensureFreeSubscription(company);
        moduleActivationService.activateDefaultModules(company);
        for (TenantModule tenantModule : tenantModuleRepository.findByCompanyId(company.getId())) {
            if (tenantModule.getStatus() != ModuleStatus.ACTIVE) {
                continue;
            }
            ModuleDefinition.fromKey(tenantModule.getModuleKey()).ifPresentOrElse(
                    module -> moduleActivationService.resyncForCompany(company, module),
                    () -> log.warn("Activated module key '{}' is not in the registry; skipping re-sync (company: {})",
                            tenantModule.getModuleKey(), company.getId()));
        }
    }

    private void ensureFreeSubscription(Company company) {
        if (subscriptionRepository.findByCompanyId(company.getId()).isPresent()) {
            return;
        }
        Plan freePlan = planRepository.findByKey(PlanDefinition.FREE.key()).orElse(null);
        if (freePlan == null) {
            log.error("FREE plan row not found; cannot backfill subscription for company: {}", company.getId());
            return;
        }
        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(freePlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);
        log.info("Backfilled FREE subscription for company: {}", company.getId());
    }
}
