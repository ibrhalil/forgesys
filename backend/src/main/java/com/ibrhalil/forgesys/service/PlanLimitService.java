package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.exception.TenantNotFoundException;
import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;

/**
 * Resolves the current tenant's plan limits (K-15 / Epic 3.0.B). Limit <em>values</em>
 * live in the code-side {@link PlanDefinition} registry — {@code t_plans} stores only
 * reference data (key/rank), so changing a limit ships with the code and needs no
 * migration. Enforcement is a caller-side soft-block: creating above the limit throws
 * 403 {@link ErrorCode#APP_LIMIT_REACHED}; existing data is never hidden or deleted.
 */
@Service
@RequiredArgsConstructor
public class PlanLimitService {

    private final CompanyRepository companyRepository;
    private final SubscriptionRepository subscriptionRepository;

    /** Max custom apps for the current tenant's plan; {@code -1} = unlimited. */
    @Transactional(readOnly = true)
    public int maxApps() {
        return activePlan().maxApps();
    }

    /** Max records per custom app for the current tenant's plan; {@code -1} = unlimited. */
    @Transactional(readOnly = true)
    public long maxRecordsPerApp() {
        return activePlan().maxRecordsPerApp();
    }

    /**
     * Throws {@link ErrorCode#APP_LIMIT_REACHED} when {@code current} is already at the
     * plan limit. Unlimited ({@code -1}) always passes. Callers pass the live count
     * (e.g. repository count) so the check and the message stay in one place.
     */
    public void assertWithin(long current, long limit, String subject) {
        if (limit >= 0 && current >= limit) {
            throw new BusinessException(ErrorCode.APP_LIMIT_REACHED,
                    "Plan limit reached for %s (%d/%d). Upgrade your plan to add more.".formatted(subject, current, limit));
        }
    }

    /**
     * The ACTIVE plan for a known company — the single plan-resolution chain
     * (K-40): Subscription -&gt; {@code t_plans.key} -&gt; {@link PlanDefinition} registry.
     * Empty when no ACTIVE subscription exists or the plan key is unknown to the
     * registry (degraded state — callers decide whether that is an error; e.g.
     * {@code ModuleActivationService} shows it in the catalog but rejects activation).
     */
    @Transactional(readOnly = true)
    public Optional<PlanDefinition> tryActivePlan(Company company) {
        Optional<String> planKey = subscriptionRepository.findByCompanyId(company.getId())
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .map(Subscription::getPlan)
                .map(Plan::getKey);
        return planKey.flatMap(key -> Arrays.stream(PlanDefinition.values())
                .filter(definition -> definition.key().equals(key))
                .findFirst());
    }

    /**
     * The current tenant's ACTIVE plan (resolved from {@link TenantContext} -&gt; Company
     * -&gt; {@link #tryActivePlan(Company)}). Throws 409
     * {@link ErrorCode#SUBSCRIPTION_NOT_FOUND} in every degraded state (no tenant
     * context and unknown schema raise {@link TenantNotFoundException}).
     */
    @Transactional(readOnly = true)
    public PlanDefinition activePlan() {
        String schemaName = TenantContext.getCurrentTenant()
                .orElseThrow(() -> new TenantNotFoundException("Tenant context is not set"));
        Company company = companyRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new TenantNotFoundException("Unknown tenant schema: " + schemaName));
        return tryActivePlan(company).orElseThrow(() ->
                new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND, "Tenant has no active subscription"));
    }
}
