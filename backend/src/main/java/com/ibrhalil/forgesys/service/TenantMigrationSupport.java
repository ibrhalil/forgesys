package com.ibrhalil.forgesys.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantMigrationSupport {

    private static final String TENANT_MIGRATION_LOCATION = "classpath:db/migration/tenant";

    private final DataSource dataSource;

    public void migrateSchema(String schemaName) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations(TENANT_MIGRATION_LOCATION)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        log.info("Flyway migrations executed for schema: {}", schemaName);
    }
}
