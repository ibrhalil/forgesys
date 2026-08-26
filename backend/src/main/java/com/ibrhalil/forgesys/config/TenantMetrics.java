package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Active-tenant-count gauge for the Prometheus scrape (K-43). Deliberately the only
 * business gauge: per-tenant series are impossible at scrape time — the scrape thread
 * has no TenantContext, so tenant schemas are unreachable there.
 */
@Configuration
public class TenantMetrics {

    @Bean
    public MeterBinder activeTenantGauge(CompanyRepository companyRepository) {
        // Runs on the scrape thread with no tenant context → the resolver falls back to
        // the public schema, exactly where t_companies lives.
        return registry -> Gauge
                .builder("forgesys.tenants.active", companyRepository,
                        repo -> repo.findAllTenantSchemas().stream()
                                .filter(t -> t.getStatus() == CompanyStatus.ACTIVE)
                                .count())
                .description("Number of ACTIVE tenants (companies) on this platform")
                .register(registry);
    }
}
