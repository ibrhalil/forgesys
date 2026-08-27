package com.ibrhalil.forgesys.service;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the plan-limit resolution (K-15 / Epic 3.0.B): TenantContext -&gt;
 * Company -&gt; ACTIVE subscription -&gt; {@link PlanDefinition} registry limits, plus the
 * soft-block predicate.
 */
@ExtendWith(MockitoExtension.class)
class PlanLimitServiceTest {

    private static final String SCHEMA = "tenant_limit_unit";

    @Mock private CompanyRepository companyRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    private PlanLimitService service;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new PlanLimitService(companyRepository, subscriptionRepository);
        company = new Company();
        company.setId(UUID.randomUUID());
        company.setSchemaName(SCHEMA);
        TenantContext.setCurrentTenant(SCHEMA);
        org.mockito.Mockito.lenient().when(companyRepository.findBySchemaName(SCHEMA))
                .thenReturn(Optional.of(company));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void maxApps_resolvesFromActivePlan() {
        stubActiveSubscription("free");
        assertThat(service.maxCustomApps()).isEqualTo(PlanDefinition.FREE.maxCustomApps());
        assertThat(service.maxRecordsPerCustomApp()).isEqualTo(PlanDefinition.FREE.maxRecordsPerCustomApp());
    }

    @Test
    void maxApps_reflectsHigherPlans() {
        stubActiveSubscription("pro");
        assertThat(service.maxCustomApps()).isEqualTo(PlanDefinition.PRO.maxCustomApps());
        stubActiveSubscription("enterprise");
        assertThat(service.maxCustomApps()).isEqualTo(-1);
    }

    @Test
    void maxApps_withoutActiveSubscription_throws() {
        Subscription pending = new Subscription();
        pending.setPlan(plan("free"));
        pending.setStatus(null);
        when(subscriptionRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.maxCustomApps())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    void maxApps_withUnknownPlanKey_throws() {
        stubActiveSubscription("mystery");

        assertThatThrownBy(() -> service.maxCustomApps())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    void assertWithin_blocksAtLimitButAllowsUnlimited() {
        assertThatThrownBy(() -> service.assertWithin(3, 3, "apps"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_LIMIT_REACHED);
        assertThatCode(() -> service.assertWithin(2, 3, "apps")).doesNotThrowAnyException();
        assertThatCode(() -> service.assertWithin(Long.MAX_VALUE, -1, "records")).doesNotThrowAnyException();
    }

    // --- helpers ---------------------------------------------------------

    private void stubActiveSubscription(String planKey) {
        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(plan(planKey));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(subscription));
    }

    private Plan plan(String key) {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setKey(key);
        plan.setName(key);
        plan.setRank(0);
        plan.setActive(true);
        return plan;
    }
}
