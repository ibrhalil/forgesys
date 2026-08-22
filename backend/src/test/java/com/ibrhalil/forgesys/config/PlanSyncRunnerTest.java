package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the plan registry sync (K-16 / Epic 3.0.A): the {@link PlanDefinition}
 * enum is upserted idempotently into {@code t_plans}.
 */
@ExtendWith(MockitoExtension.class)
class PlanSyncRunnerTest {

    @Mock private PlanRepository planRepository;
    @Mock private ObjectProvider<PlanSyncRunner> self;

    private PlanSyncRunner runner;

    @BeforeEach
    void setUp() {
        runner = new PlanSyncRunner(planRepository, self);
        when(self.getObject()).thenReturn(runner);
    }

    @Test
    void syncPlans_insertsMissingPlans() {
        when(planRepository.findByKey(any(String.class))).thenReturn(Optional.empty());
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository, org.mockito.Mockito.times(PlanDefinition.values().length)).save(captor.capture());
        List<String> keys = captor.getAllValues().stream().map(Plan::getKey).toList();
        assertThat(keys).containsExactlyInAnyOrder(
                java.util.Arrays.stream(PlanDefinition.values()).map(PlanDefinition::key).toArray(String[]::new));
        assertThat(captor.getAllValues()).allSatisfy(plan -> {
            assertThat(plan.isActive()).isTrue();
            assertThat(plan.getName()).isNotBlank();
        });
    }

    @Test
    void syncPlans_updatesExistingPlanRow() {
        Plan existing = new Plan();
        existing.setKey("free");
        existing.setName("Legacy Name");
        existing.setRank(99);
        when(planRepository.findByKey("free")).thenReturn(Optional.of(existing));
        when(planRepository.findByKey("pro")).thenReturn(Optional.empty());
        when(planRepository.findByKey("enterprise")).thenReturn(Optional.empty());
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository, org.mockito.Mockito.times(PlanDefinition.values().length)).save(captor.capture());
        Plan free = captor.getAllValues().stream().filter(p -> p.getKey().equals("free")).findFirst().orElseThrow();
        assertThat(free.getName()).isEqualTo("Free");
        assertThat(free.getRank()).isZero();
    }
}
