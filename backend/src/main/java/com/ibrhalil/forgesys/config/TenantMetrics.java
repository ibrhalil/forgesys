package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Business gauge for the Prometheus scrape (K-43 / Faz 5 observability).
 * Deliberately minimal: JVM/HTTP/system series come from Micrometer's
 * auto-configured binders — the only business number that is meaningful
 * per-process (not per-tenant) is the active tenant count. Tenant-schema
 * gauges (apps/records per tenant) are intentionally NOT attempted: the scrape
 * thread has no TenantContext, so tenant schemas are unreachable there;
 * per-tenant series would need a labeled push design, not a scrape-time gauge.
 */
@Configuration
public class TenantMetrics {

    @Bean
    public MeterBinder activeTenantGauge(CompanyRepository companyRepository) {
        // The value function runs on the scrape thread. No tenant context is set
        // there, so the multi-tenant resolver falls back to the public schema —
        // exactly where t_companies lives. One lightweight projection query
        // (K-40 TenantSchemaView, no entity hydration) per scrape.
        return registry -> Gauge
                .builder("forgesys.tenants.active", companyRepository,
                        repo -> repo.findAllTenantSchemas().stream()
                                .filter(t -> t.getStatus() == CompanyStatus.ACTIVE)
                                .count())
                .description("Number of ACTIVE tenants (companies) on this platform")
                .register(registry);
    }
}
