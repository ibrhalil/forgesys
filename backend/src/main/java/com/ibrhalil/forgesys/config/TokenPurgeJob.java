package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantVerificationTokenRepository;
import com.ibrhalil.forgesys.service.UserTokenService;
import com.ibrhalil.forgesys.tenant.TenantContextExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * [RISK-30] Daily 03:00 UTC purge of stale single-use tokens: public signup tokens
 * ({@code t_tenant_verification_tokens}) + per-tenant {@code t_auth_tokens} (set-and-restore
 * tenant iteration, per-tenant try/catch). Rows consumed/expired more than
 * {@code verification-token-retention-days} days ago are deleted.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class TokenPurgeJob {

    private final TenantVerificationTokenRepository signupTokenRepository;
    private final UserTokenService userTokenService;
    private final CompanyRepository companyRepository;
    private final ObjectProvider<TokenPurgeJob> self;

    @Value("${forgesys.security.verification-token-retention-days:7}")
    private long retentionDays;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void purgeStaleTokens() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        self.getObject().purgeSignupTokens(cutoff);
        purgeTenantUserTokens(cutoff);
    }

    @Transactional
    public void purgeSignupTokens(OffsetDateTime cutoff) {
        int purged = signupTokenRepository.purgeStale(cutoff);
        if (purged > 0) {
            log.info("Purged {} stale tenant verification tokens (cutoff {})", purged, cutoff);
        }
    }

    private void purgeTenantUserTokens(OffsetDateTime cutoff) {
        for (CompanyRepository.TenantSchemaView tenant : companyRepository.findAllTenantSchemas()) {
            try {
                TenantContextExecutor.inTenantContext(tenant.getSchemaName(),
                        () -> logPurged(userTokenService.purgeStaleForCurrentTenant(cutoff), tenant.getSchemaName()));
            } catch (Exception e) {
                log.error("User auth token purge failed for tenant {}", tenant.getSchemaName(), e);
            }
        }
    }

    private void logPurged(int purged, String schemaName) {
        if (purged > 0) {
            log.info("Purged {} stale user auth tokens for tenant {}", purged, schemaName);
        }
    }
}
