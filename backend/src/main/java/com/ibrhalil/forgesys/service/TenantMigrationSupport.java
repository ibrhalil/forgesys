package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.config.ModuleDefinition;
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
    private static final String MODULE_HISTORY_TABLE_PATTERN = "flyway_schema_history_mod_%s";

    private final DataSource dataSource;

    public void migrateSchema(String schemaName) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations(TENANT_MIGRATION_LOCATION)
                .load();
        flyway.migrate();
        log.info("Flyway migrations executed for schema: {}", schemaName);
    }

    /**
     * Runs a module's own migrations ({@code db/migration/module/{key}}) against a
     * module-scoped history table ({@code flyway_schema_history_mod_<key>}) — module
     * versions never collide with core versions (K-16). Module locations deliberately
     * live OUTSIDE {@code db/migration/tenant} (a recursive scan would swallow them
     * into the core history). Null {@link ModuleDefinition#flywayLocation()} = no-op
     * (tables ship in the core tenant baseline).
     */
    public void migrateModule(String schemaName, ModuleDefinition module) {
        String location = module.flywayLocation();
        if (location == null) {
            log.debug("Module '{}' ships in the tenant baseline; no module migration to run", module.key());
            return;
        }
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations(location)
                .table(MODULE_HISTORY_TABLE_PATTERN.formatted(module.key()))
                // baselineOnMigrate + version 0: the schema is non-empty but the module
                // history table isn't — Flyway demands a baseline there. Version 0 records
                // "nothing applied" and skips NOTHING (unlike the core history — K-36).
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
        flyway.migrate();
        log.info("Flyway module migrations executed for schema: {} module: {}", schemaName, module.key());
    }
}
