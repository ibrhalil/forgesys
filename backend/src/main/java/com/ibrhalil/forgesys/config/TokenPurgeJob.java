package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantVerificationTokenRepository;
import com.ibrhalil.forgesys.service.UserTokenService;
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
 * [RISK-30] Daily purge of stale single-use tokens:
 * <ol>
 *   <li>Signup verification tokens in the {@code public} schema
 *       ({@code t_tenant_verification_tokens}).</li>
 *   <li>User lifecycle tokens (email verify / password reset) in EVERY tenant schema
 *       ({@code t_auth_tokens}) — iterated with the {@code TenantMigrationRunner}
 *       set-and-restore pattern; {@link UserTokenService#purgeStaleForCurrentTenant}
 *       opens the per-tenant transaction.</li>
 * </ol>
 * Rows consumed ({@code used_at}) or expired ({@code expires_at}) more than
 * {@code forgesys.security.verification-token-retention-days} days ago are deleted.
 * Per-tenant failures are isolated (try/catch) — one broken schema never blocks the
 * rest.
 *
 * <p>The signup purge runs through the {@code self} proxy (same pattern as
 * {@code TenantProvisioningService.createAdminUser}): {@code @Scheduled} entry point
 * and transactional worker live in one class without a self-invocation trap.
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

    /** Daily at 03:00 UTC (off-peak). */
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
                TenantContext.setCurrentTenant(tenant.getSchemaName());
                int purged = userTokenService.purgeStaleForCurrentTenant(cutoff);
                if (purged > 0) {
                    log.info("Purged {} stale user auth tokens for tenant {}", purged, tenant.getSchemaName());
                }
            } catch (Exception e) {
                log.error("User auth token purge failed for tenant {}", tenant.getSchemaName(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
