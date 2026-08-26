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
 * Provisions the reserved {@code system} tenant + admin at startup (K-24) via the
 * auto-verify path with mail suppressed — bootstrap must not depend on an email loop.
 * Idempotent (subdomain check); failures are logged and swallowed so bootstrap never
 * aborts startup.
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
