package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.persistence.tenant.SchemaPerTenantConnectionProvider;
import com.ibrhalil.forgesys.persistence.tenant.TenantIdentifierResolver;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Configuration
@EntityScan(basePackages = "com.ibrhalil.forgesys.entity")
@EnableJpaRepositories(basePackages = "com.ibrhalil.forgesys.persistence.repository")
@EnableJpaAuditing(auditorAwareRef = "auditorAwareProvider", dateTimeProviderRef = "dateTimeProvider")
public class MultiTenancyJpaConfig {

    @Bean
    public MultiTenantConnectionProvider<String> multiTenantConnectionProvider(DataSource dataSource) {
        return new SchemaPerTenantConnectionProvider(dataSource);
    }

    @Bean
    public CurrentTenantIdentifierResolver<String> currentTenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    public HibernatePropertiesCustomizer multiTenancyPropertiesCustomizer(
            MultiTenantConnectionProvider<String> connectionProvider,
            CurrentTenantIdentifierResolver<String> tenantResolver) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }

    /**
     * Audit actor from the SecurityContext (RISK-33); falls back to {@code "system"}
     * for signup, provisioning and startup runners. K-50 F6: impersonated sessions
     * attribute mutations to the acting platform identity ({@code act}, frozen
     * decision #5) — the target admin is a vehicle, not the actor.
     */
    @Bean
    public AuditorAware<String> auditorAwareProvider() {
        return () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails user) {
                if (user.isImpersonation() && user.getActUserId() != null) {
                    return Optional.of(user.getActUserId());
                }
                return Optional.of(user.getUserId().toString());
            }
            return Optional.of("system");
        };
    }

    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
