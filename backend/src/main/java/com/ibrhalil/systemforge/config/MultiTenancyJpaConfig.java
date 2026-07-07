package com.ibrhalil.systemforge.config;

import com.ibrhalil.systemforge.persistence.tenant.SchemaPerTenantConnectionProvider;
import com.ibrhalil.systemforge.persistence.tenant.TenantIdentifierResolver;
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

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Configuration
@EntityScan(basePackages = "com.ibrhalil.systemforge.entity")
@EnableJpaRepositories(basePackages = "com.ibrhalil.systemforge.persistence.repository")
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

    @Bean
    public AuditorAware<String> auditorAwareProvider() {
        return () -> Optional.of("system");
    }

    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
