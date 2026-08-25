package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantVerificationTokenRepository;
import com.ibrhalil.forgesys.service.UserTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [RISK-30] The purge job sweeps both token families with a cutoff {@code retention
 * days} in the past: public signup tokens (through the self proxy — transactional
 * worker) and per-tenant user auth tokens (set-and-restore TenantContext iteration,
 * per-tenant failure isolation). Pure unit test — cron wiring is Spring plumbing.
 */
@ExtendWith(MockitoExtension.class)
class TokenPurgeJobTest {

    @Mock private TenantVerificationTokenRepository signupTokenRepository;
    @Mock private UserTokenService userTokenService;
    @Mock private CompanyRepository companyRepository;
    @Mock private ObjectProvider<TokenPurgeJob> self;

    private TokenPurgeJob job;

    @BeforeEach
    void setUp() {
        job = new TokenPurgeJob(signupTokenRepository, userTokenService, companyRepository, self);
        ReflectionTestUtils.setField(job, "retentionDays", 7L);
        // Self proxy: the unit test has no proxy — return the job itself.
        lenient().when(self.getObject()).thenReturn(job);
    }

    @AfterEach
    void cleanTenantContext() {
        TenantContext.clear();
    }

    @Test
    void purgeSweepsSignupTokensWithCutoffSevenDaysAgo() {
        when(signupTokenRepository.purgeStale(any(OffsetDateTime.class))).thenReturn(3);

        job.purgeStaleTokens();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(signupTokenRepository).purgeStale(cutoff.capture());
        assertThat(cutoff.getValue())
                .isCloseTo(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7), within(java.time.Duration.ofSeconds(5)));
    }

    @Test
    void purgeIteratesEveryTenantSchemaForUserTokens() {
        when(companyRepository.findAllTenantSchemas()).thenReturn(List.of(
                tenant("tenant_a"), tenant("tenant_b")));

        job.purgeStaleTokens();

        verify(userTokenService, org.mockito.Mockito.times(2)).purgeStaleForCurrentTenant(any(OffsetDateTime.class));
        // The TenantContext window closes after the sweep — no ThreadLocal leak.
        assertThat(TenantContext.getCurrentTenant()).isEmpty();
    }

    @Test
    void oneBrokenTenantDoesNotBlockTheOthers() {
        when(companyRepository.findAllTenantSchemas()).thenReturn(List.of(
                tenant("tenant_a"), tenant("tenant_b")));
        when(userTokenService.purgeStaleForCurrentTenant(any(OffsetDateTime.class)))
                .thenThrow(new RuntimeException("schema gone"))
                .thenReturn(1);

        job.purgeStaleTokens();

        verify(userTokenService, org.mockito.Mockito.times(2)).purgeStaleForCurrentTenant(any());
    }

    @Test
    void zeroDeletedRowsIsSilentSuccess() {
        when(signupTokenRepository.purgeStale(any(OffsetDateTime.class))).thenReturn(0);
        when(companyRepository.findAllTenantSchemas()).thenReturn(List.of());

        job.purgeStaleTokens();

        verify(signupTokenRepository).purgeStale(any(OffsetDateTime.class));
    }

    private CompanyRepository.TenantSchemaView tenant(String schemaName) {
        return new CompanyRepository.TenantSchemaView() {
            @Override public UUID getId() { return UUID.randomUUID(); }
            @Override public String getSchemaName() { return schemaName; }
            @Override public com.ibrhalil.forgesys.entity.CompanyStatus getStatus() { return com.ibrhalil.forgesys.entity.CompanyStatus.ACTIVE; }
        };
    }
}
