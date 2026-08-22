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
     * Runs a module's own tenant migrations ({@code db/migration/module/{key}}) in
     * the given tenant schema against a module-scoped history table
     * ({@code flyway_schema_history_mod_{key}}) — isolated from the core tenant history,
     * so module versions never collide with core versions and each module versions
     * independently from V1 (K-16 / Epic 3.0.A). Module locations deliberately live
     * OUTSIDE {@code db/migration/tenant} (recursive scan would swallow them into the
     * core history). Modules whose tables ship in the core tenant baseline
     * ({@link ModuleDefinition#flywayLocation()} == {@code null}) are a no-op.
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
                // The tenant schema is always non-empty when a module activates (core
                // tables + core history exist), and the module history table does not
                // exist yet on first activation — Flyway demands a baseline in that
                // state. Baseline version 0 records "nothing applied" and skips NOTHING:
                // every module migration (V1+) still runs. (Unlike the core history,
                // where baselineOnMigrate is intentionally avoided — K-36.)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
        flyway.migrate();
        log.info("Flyway module migrations executed for schema: {} module: {}", schemaName, module.key());
    }
}
