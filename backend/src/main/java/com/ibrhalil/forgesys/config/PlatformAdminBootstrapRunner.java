package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.PlatformUser;
import com.ibrhalil.forgesys.entity.PlatformUserType;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.service.PlatformAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the initial platform superadmin at startup (K-50 — K-24 pattern). Idempotent
 * by email; failures are logged and swallowed so bootstrap never aborts startup.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
@EnableConfigurationProperties(PlatformAdminBootstrapProperties.class)
@Order(1)
public class PlatformAdminBootstrapRunner implements ApplicationRunner {

    private final PlatformAdminBootstrapProperties properties;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService platformAuditService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Platform admin bootstrap disabled, skipping");
            return;
        }
        if (!isConfigured()) {
            log.warn("Platform admin bootstrap enabled but required properties are missing, skipping");
            return;
        }
        if (platformUserRepository.existsByEmail(properties.email())) {
            log.info("Platform admin already exists (email={}), skipping bootstrap", properties.email());
            return;
        }
        try {
            PlatformUser admin = new PlatformUser();
            admin.setEmail(properties.email());
            admin.setDisplayName(properties.displayName() != null && !properties.displayName().isBlank()
                    ? properties.displayName() : properties.email());
            admin.setUserType(PlatformUserType.HUMAN);
            admin.setPasswordHash(passwordEncoder.encode(properties.password()));
            admin.setEnabled(true);
            platformUserRepository.save(admin);
            platformAuditService.record(admin.getId(), PlatformAuditService.ACTOR_SYSTEM,
                    "platform_admin_bootstrapped", "platform_user", admin.getId(), null);
            log.info("Platform admin bootstrapped (email={})", properties.email());
        } catch (Exception e) {
            log.error("Failed to bootstrap platform admin (email={})", properties.email(), e);
        }
    }

    private boolean isConfigured() {
        return notBlank(properties.email()) && notBlank(properties.password());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
