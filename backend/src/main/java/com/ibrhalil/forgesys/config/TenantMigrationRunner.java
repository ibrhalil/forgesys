package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.service.TenantMigrationSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class TenantMigrationRunner implements ApplicationRunner {

    private final CompanyRepository companyRepository;
    private final TenantMigrationSupport tenantMigrationSupport;

    @Override
    public void run(ApplicationArguments args) {
        List<Company> companies = companyRepository.findAll();
        if (companies.isEmpty()) {
            log.info("No tenants found, skipping tenant migration");
            return;
        }
        log.info("Migrating {} tenant schema(s) at startup", companies.size());
        for (Company company : companies) {
            String schemaName = company.getSchemaName();
            if (schemaName == null || schemaName.isBlank()) {
                log.warn("Skipping tenant with blank schema name: id={}", company.getId());
                continue;
            }
            try {
                tenantMigrationSupport.migrateSchema(schemaName);
            } catch (Exception e) {
                log.error("Failed to migrate tenant schema: {}", schemaName, e);
            }
        }
    }
}
