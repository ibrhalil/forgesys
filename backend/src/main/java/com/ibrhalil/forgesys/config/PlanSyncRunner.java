package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent upsert of the {@link PlanDefinition} registry into {@code t_plans} (public
 * schema) at startup (K-16 / Epic 3.0.A). Ordered to run before every other runner —
 * {@code SystemAdminBootstrapRunner} (tenant provisioning writes a subscription) and
 * {@code ModuleSyncRunner} (subscription backfill) depend on the plan rows existing.
 *
 * <p>Disabled in the {@code test} profile (tests build plan fixtures manually).
 */
@Slf4j
@Component
@Profile("!test")
@Order(0)
@RequiredArgsConstructor
public class PlanSyncRunner implements ApplicationRunner {

    private final PlanRepository planRepository;
    private final ObjectProvider<PlanSyncRunner> self;

    @Override
    public void run(ApplicationArguments args) {
        self.getObject().syncPlans();
    }

    @Transactional
    public void syncPlans() {
        for (PlanDefinition definition : PlanDefinition.values()) {
            Plan plan = planRepository.findByKey(definition.key()).orElseGet(() -> {
                log.info("Seeding plan: {}", definition.key());
                return new Plan();
            });
            plan.setKey(definition.key());
            plan.setName(definition.displayName());
            plan.setRank(definition.rank());
            plan.setActive(true);
            planRepository.save(plan);
        }
    }
}
