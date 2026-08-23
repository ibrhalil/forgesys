package com.ibrhalil.forgesys;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.ModuleStatus;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.entity.TenantModule;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantModuleRepository;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.service.ModuleActivationService;
import com.ibrhalil.forgesys.service.TenantMigrationSupport;
import com.ibrhalil.forgesys.service.TenantProvisioningService;
import org.hibernate.dialect.PostgreSQLDialect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Module system against a real PostgreSQL (K-16 / Epic 3.0.A, gated like
 * {@code CrossTenantIsolationTest}). Validates the full provisioning hook (FREE
 * subscription + default module activation + tenant-schema permission seed — including
 * the FK-deadlock-free transaction split) and the module-scoped Flyway history isolation
 * ({@code flyway_schema_history_mod_<key>} separate from the core tenant history).
 *
 * <p><strong>Gated:</strong> skipped unless {@code -Dforgesys.pg.it=true} is set. Run with:
 * <pre>{@code
 * ./mvnw -pl backend test -Dtest=ModuleActivationIT -Dforgesys.pg.it=true
 * }</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "forgesys.pg.it", matches = "true")
@Import(ModuleActivationIT.ContainerConfig.class)
class ModuleActivationIT {

    private static final String SUBDOMAIN = "modit";

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.dialect", () -> PostgreSQLDialect.class.getName());
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/public");
    }

    @Autowired private CompanyRepository companyRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private TenantModuleRepository tenantModuleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private TenantProvisioningService provisioningService;
    @Autowired private ModuleActivationService moduleActivationService;
    @Autowired private TenantMigrationSupport tenantMigrationSupport;
    @Autowired private DataSource dataSource;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private Company company;
    private String schemaName;

    @BeforeAll
    void provisionTenant() {
        // PlanSyncRunner is absent in the test profile — seed the plan registry manually.
        for (PlanDefinition definition : PlanDefinition.values()) {
            Plan plan = planRepository.findByKey(definition.key()).orElseGet(Plan::new);
            plan.setKey(definition.key());
            plan.setName(definition.displayName());
            plan.setRank(definition.rank());
            plan.setActive(true);
            planRepository.save(plan);
        }
        provisioningService.provisionSystemTenant(new CompanyRegisterRequest(
                "Module IT", SUBDOMAIN, "admin@modit.test", "Secret123!", "Admin", "IT"));
        company = companyRepository.findBySubdomain(SUBDOMAIN).orElseThrow();
        schemaName = company.getSchemaName();
    }

    @Test
    void provisioningWritesFreeSubscriptionAndDefaultModule() {
        String planKey = inTx(() -> subscriptionRepository.findByCompanyId(company.getId())
                .map(subscription -> subscription.getPlan().getKey())
                .orElseThrow());
        assertThat(planKey).isEqualTo(PlanDefinition.FREE.key());

        TenantModule pm = tenantModuleRepository.findByCompanyIdAndModuleKey(company.getId(), "pm").orElseThrow();
        assertThat(pm.getStatus()).isEqualTo(ModuleStatus.ACTIVE);
    }

    @Test
    void modulePermissionsAreSeededInTenantSchema() {
        TenantContext.setCurrentTenant(schemaName);
        try {
            List<String> names = permissionRepository.findAll().stream()
                    .map(Permission::getName)
                    .toList();
            // Provisioning activates the DEFAULT module set (test profile: built-in
            // fallback "pm" — the registry's APPS ships but is opt-in here), so exactly
            // the default modules' permissions must be seeded.
            for (String key : new com.ibrhalil.forgesys.config.ModuleProperties(null).effectiveDefaultKeys()) {
                ModuleDefinition def = ModuleDefinition.fromKey(key).orElseThrow();
                def.permissions().forEach(expected ->
                        assertThat(names).contains(expected.name()));
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void reActivationIsIdempotentOnRealPostgres() {
        moduleActivationService.activateDefaultModules(company);
        moduleActivationService.activateDefaultModules(company);

        List<TenantModule> rows = tenantModuleRepository.findByCompanyId(company.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getModuleKey()).isEqualTo("pm");
    }

    /**
     * K-45 step 4 on real PostgreSQL: activating the notes module runs
     * {@code module/notes/V1+V2} — notes/categories gain their {@code project_id}
     * (NOT NULL) and the tenant's default NOTES container ("Genel") exists.
     */
    @Test
    void notesActivationScopesNotesAndEnsuresDefaultProject() throws Exception {
        moduleActivationService.activateForCompany(company, ModuleDefinition.NOTES);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT count(*) FROM " + schemaName + ".t_projects"
                             + " WHERE project_type = 'NOTES' AND is_default = true AND is_deleted = false")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("exactly one default NOTES container").isEqualTo(1);
        }
        assertThat(columnNullable("t_notes", "project_id")).as("t_notes.project_id NOT NULL").isEqualTo("NO");
        assertThat(columnNullable("t_note_categories", "project_id"))
                .as("t_note_categories.project_id NOT NULL").isEqualTo("NO");
        assertThat(tenantModuleRepository.findByCompanyIdAndModuleKey(company.getId(), "notes")).isPresent();
    }

    /**
     * A module with its own Flyway location migrates against a module-scoped history
     * table ({@code flyway_schema_history_mod_demo}) — isolated from the core tenant
     * history, versions never collide. Uses a Mockito-mocked {@link ModuleDefinition}
     * pointing at the demo location shipped in test resources.
     */
    @Test
    void moduleMigrationsRunInTheirOwnHistoryTable() throws Exception {
        ModuleDefinition demo = mock(ModuleDefinition.class);
        when(demo.key()).thenReturn("demo");
        when(demo.flywayLocation()).thenReturn(ModuleDefinition.FLYWAY_LOCATION_PATTERN.formatted("demo"));
        tenantMigrationSupport.migrateModule(schemaName, demo);

        assertThat(regclassExists(schemaName + ".t_demo")).as("demo module table created in tenant schema").isTrue();
        assertThat(regclassExists(schemaName + ".flyway_schema_history_mod_demo"))
                .as("module-scoped Flyway history table exists").isTrue();
        // The core tenant history must NOT contain the demo migration.
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT count(*) FROM " + schemaName + ".flyway_schema_history WHERE version = '1' AND script LIKE '%demo%'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    // --- helpers ---------------------------------------------------------

    /** Runs a read inside a short transaction (lazy plan proxy needs an open session). */
    private <T> T inTx(java.util.function.Supplier<T> work) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(status -> work.get());
    }

    private boolean regclassExists(String qualifiedName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT to_regclass('" + qualifiedName + "') IS NOT NULL")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private String columnNullable(String table, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT is_nullable FROM information_schema.columns"
                             + " WHERE table_schema = '" + schemaName + "' AND table_name = '" + table + "'"
                             + " AND column_name = '" + column + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }
}
