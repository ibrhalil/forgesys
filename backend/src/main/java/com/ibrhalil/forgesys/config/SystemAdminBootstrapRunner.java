package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.service.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Provisions the reserved {@code system} tenant + its admin user at startup, so
 * the platform has a stable privileged identity without a manual signup. Calls
 * {@link TenantProvisioningService#provisionSystemTenant(CompanyRegisterRequest)},
 * which runs the K-21 two-phase flow back-to-back with verification mail suppressed
 * (bootstrap must not depend on an email loop).
 *
 * <p>Idempotent: if a company with the configured subdomain already exists the
 * runner does nothing. Failures are logged and swallowed so a bootstrap error
 * never aborts application startup. The RBAC seed (Admin role + platform/iam
 * permissions) and the explicit Admin grant for this admin are applied by
 * {@code TenantProvisioningService.createAdminUser} (via {@code RbacSeeder})
 * during provisioning itself — this runner intentionally only performs tenant +
 * admin provisioning.
 *
 * <p>Disabled in the {@code test} profile (mirrors {@code TenantMigrationRunner}).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
@EnableConfigurationProperties(SystemAdminBootstrapProperties.class)
@Order(1)
public class SystemAdminBootstrapRunner implements ApplicationRunner {

    private final SystemAdminBootstrapProperties properties;
    private final TenantProvisioningService tenantProvisioningService;
    private final CompanyRepository companyRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("System admin bootstrap disabled, skipping");
            return;
        }
        if (!isConfigured()) {
            log.warn("System admin bootstrap enabled but required properties are missing, skipping");
            return;
        }
        if (companyRepository.findBySubdomain(properties.subdomain()).isPresent()) {
            log.info("System tenant already exists (subdomain={}), skipping bootstrap", properties.subdomain());
            return;
        }
        try {
            CompanyRegisterRequest request = new CompanyRegisterRequest(
                    properties.companyName(),
                    properties.subdomain(),
                    properties.email(),
                    properties.password(),
                    properties.firstName(),
                    properties.lastName()
            );
            // Auto-verify path (K-24): createPendingCompany + verifyAndProvision in one call,
            // verification mail suppressed — bootstrap must not depend on an email loop.
            tenantProvisioningService.provisionSystemTenant(request);
            log.info("System tenant provisioned (subdomain={}, email={})", properties.subdomain(), properties.email());
        } catch (Exception e) {
            log.error("Failed to provision system tenant (subdomain={})", properties.subdomain(), e);
        }
    }

    private boolean isConfigured() {
        return notBlank(properties.companyName())
                && notBlank(properties.subdomain())
                && notBlank(properties.email())
                && notBlank(properties.password());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
