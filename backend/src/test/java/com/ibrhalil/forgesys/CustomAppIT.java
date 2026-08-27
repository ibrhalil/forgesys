package com.ibrhalil.forgesys;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.dto.CustomAppPropertyConfigDto;
import com.ibrhalil.forgesys.dto.CustomAppPropertyRequest;
import com.ibrhalil.forgesys.dto.CustomAppPropertyResponse;
import com.ibrhalil.forgesys.dto.CustomAppRecordRequest;
import com.ibrhalil.forgesys.dto.CustomAppRecordResponse;
import com.ibrhalil.forgesys.dto.CustomAppRecordSearchRequest;
import com.ibrhalil.forgesys.dto.CustomAppRequest;
import com.ibrhalil.forgesys.dto.CustomAppResponse;
import com.ibrhalil.forgesys.dto.CustomAppValueFilterCriteria;
import com.ibrhalil.forgesys.dto.CustomAppValueOperator;
import com.ibrhalil.forgesys.dto.CustomAppValueSortCriteria;
import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.service.CustomAppService;
import com.ibrhalil.forgesys.service.CustomAppRecordService;
import com.ibrhalil.forgesys.service.ModuleActivationService;
import com.ibrhalil.forgesys.service.TenantProvisioningService;
import com.ibrhalil.forgesys.service.mail.InMemoryMailSender;
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
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Custom CustomApp Builder against a real PostgreSQL (K-15 / Epic 3.0.B, gated like
 * {@code ModuleActivationIT} / {@code CrossTenantIsolationTest}). Validates the whole
 * module path: activation of the first {@code ownMigrations} module (per-module Flyway
 * history + PG-only DDL: jsonb columns + GIN index + partial uniques), the JSONB EAV
 * record lifecycle through the real services, the native containment/comparison search
 * and two-tenant schema isolation.
 *
 * <p><strong>Gated:</strong> skipped unless {@code -Dforgesys.pg.it=true} is set. Run with:
 * <pre>{@code
 * ./mvnw -pl backend -am test -Dtest=CustomAppIT -Dforgesys.pg.it=true
 * }</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "forgesys.pg.it", matches = "true")
@Import(CustomAppIT.ContainerConfig.class)
class CustomAppIT {

    private static final String SUBDOMAIN = "appit";
    private static final String SUBDOMAIN_2 = "appit2";

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
        // Mirror of the dev/prod JDBC URLs: binds Strings as unspecified so PG casts
        // them into jsonb columns (AuditLog + the app builder JSONB columns).
        registry.add("spring.datasource.hikari.data-source-properties.stringtype", () -> "unspecified");
    }

    @Autowired private CompanyRepository companyRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private TenantProvisioningService provisioningService;
    @Autowired private InMemoryMailSender mailSender;
    @Autowired private ModuleActivationService moduleActivationService;
    @Autowired private CustomAppService customAppService;
    @Autowired private CustomAppRecordService customAppRecordService;
    @Autowired private ObjectMapper objectMapper;
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
        TenantProvisioningTestSupport.provisionViaTwoPhaseFlow(provisioningService, mailSender,
                new CompanyRegisterRequest(
                        "CustomApp Builder IT", SUBDOMAIN, "admin@appit.test", "Secret123!", "Admin", "IT"));
        company = companyRepository.findBySubdomain(SUBDOMAIN).orElseThrow();
        schemaName = company.getSchemaName();
    }

    @Test
    void appsModuleActivatesWithItsOwnMigrationHistory() throws Exception {
        activateApps(company);

        assertThat(regclassExists(schemaName + ".t_custom_apps")).as("t_custom_apps created").isTrue();
        assertThat(regclassExists(schemaName + ".t_custom_app_properties")).as("t_custom_app_properties created").isTrue();
        assertThat(regclassExists(schemaName + ".t_custom_app_records")).as("t_custom_app_records created").isTrue();
        assertThat(regclassExists(schemaName + ".t_custom_app_record_values")).as("t_custom_app_record_values created").isTrue();
        assertThat(regclassExists(schemaName + ".t_custom_app_views")).as("t_custom_app_views created").isTrue();
        // GIN index backing the JSONB search.
        assertThat(regclassExists(schemaName + ".idx_custom_app_record_values_value"))
                .as("GIN index on value jsonb_path_ops").isTrue();
        // Module history isolated from the core tenant history.
        assertThat(regclassExists(schemaName + ".flyway_schema_history_mod_apps"))
                .as("module-scoped Flyway history table").isTrue();
        assertThat(coreHistoryContains("apps")).as("core history must not contain module migrations").isFalse();

        TenantContext.setCurrentTenant(schemaName);
        try {
            List<String> names = permissionRepository.findAll().stream().map(Permission::getName).toList();
            for (var definition : ModuleDefinition.APPS.permissions()) {
                assertThat(names).contains(definition.name());
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void recordLifecycleAndJsonbSearch() {
        activateApps(company);
        inTenant(() -> {
            CustomAppResponse app = customAppService.create(new CustomAppRequest("CRM", "IT pipeline", null, null));
            CustomAppPropertyResponse name = customAppService.addProperty(app.id(), new CustomAppPropertyRequest(
                    "Name", PropertyType.TEXT, null, true, 0));
            CustomAppPropertyResponse amount = customAppService.addProperty(app.id(), new CustomAppPropertyRequest(
                    "Amount", PropertyType.NUMBER, null, null, 1));
            CustomAppPropertyResponse stage = customAppService.addProperty(app.id(), new CustomAppPropertyRequest(
                    "Stage", PropertyType.SELECT, new CustomAppPropertyConfigDto(List.of("open", "won"), null), null, 2));

            UUID acme = createRecord(app.id(), Map.of(
                    name.id(), json("\"Acme Corp\""),
                    amount.id(), json("1500"),
                    stage.id(), json("\"open\"")));
            UUID globex = createRecord(app.id(), Map.of(
                    name.id(), json("\"Globex\""),
                    amount.id(), json("9000"),
                    stage.id(), json("\"won\"")));
            UUID initech = createRecord(app.id(), Map.of(
                    name.id(), json("\"Initech\""),
                    stage.id(), json("\"open\"")));

            // EQ (containment equality on jsonb)
            assertThat(searchIds(app.id(), List.of(filter(stage, CustomAppValueOperator.EQ, json("\"open\""))), null))
                    .containsExactlyInAnyOrder(acme, initech);
            // GT numeric
            assertThat(searchIds(app.id(), List.of(filter(amount, CustomAppValueOperator.GT, json("2000"))), null))
                    .containsExactly(globex);
            // CONTAINS text
            assertThat(searchIds(app.id(), List.of(filter(name, CustomAppValueOperator.CONTAINS, json("\"corp\""))), null))
                    .containsExactly(acme);
            // IS_EMPTY (no value row)
            assertThat(searchIds(app.id(), List.of(filter(amount, CustomAppValueOperator.IS_EMPTY, null)), null))
                    .containsExactly(initech);

            // Sort by numeric property DESC — NULLS semantics come from the subquery (empty last is not
            // guaranteed by PG for text; numeric NULLIF sorts nulls last on DESC by default... explicitly
            // assert only the relative order of the two non-empty rows and membership of the third).
            List<UUID> byAmount = searchIds(app.id(), null,
                    List.of(new CustomAppValueSortCriteria(amount.id().toString(), "desc")));
            assertThat(byAmount.indexOf(globex)).isLessThan(byAmount.indexOf(acme));
            assertThat(byAmount).contains(initech);
            // Sort by createdAt (reserved key) DESC — newest record first.
            assertThat(searchIds(app.id(), null, List.of(new CustomAppValueSortCriteria("createdAt", "desc")))
                    .getFirst()).isEqualTo(initech);

            // PATCH: clear the optional stage of Acme — now IS_EMPTY on stage matches only Acme
            // (Globex has "won", Initech has "open").
            customAppRecordService.update(app.id(), acme, new CustomAppRecordRequest(
                    Map.of(stage.id().toString(), json("null"))));
            assertThat(searchIds(app.id(), List.of(filter(stage, CustomAppValueOperator.IS_EMPTY, null)), null))
                    .containsExactly(acme);

            return null;
        });
    }

    @Test
    void appsAreIsolatedPerTenantSchema() {
        activateApps(company);
        TenantProvisioningTestSupport.provisionViaTwoPhaseFlow(provisioningService, mailSender,
                new CompanyRegisterRequest(
                        "CustomApp Builder IT 2", SUBDOMAIN_2, "admin@appit2.test", "Secret123!", "Admin", "IT"));
        Company company2 = companyRepository.findBySubdomain(SUBDOMAIN_2).orElseThrow();
        activateApps(company2);

        UUID appInSecond = inTenantOf(company2, () ->
                customAppService.create(new CustomAppRequest("Tenant B CustomApp", null, null, null)).id());
        UUID appInFirst = inTenant(schemaName, () ->
                customAppService.create(new CustomAppRequest("Tenant A CustomApp", null, null, null)).id());

        // Each tenant sees only its own apps — schema-per-tenant isolation.
        inTenant(schemaName, () -> {
            assertThatThrownBy(() -> customAppService.findById(appInSecond))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThat(customAppService.findById(appInFirst).name()).isEqualTo("Tenant A CustomApp");
            return null;
        });
        inTenantOf(company2, () -> {
            assertThatThrownBy(() -> customAppService.findById(appInFirst))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThat(customAppService.findById(appInSecond).name()).isEqualTo("Tenant B CustomApp");
            return null;
        });
    }

    // --- helpers ---------------------------------------------------------

    /**
     * Activation joins the CALLER's transaction by design (K-16 FK-deadlock split) —
     * the IT must scope one, or the lazy {@code Subscription.plan} proxy detonates
     * outside a session.
     */
    private void activateApps(Company target) {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        moduleActivationService.activateForCompany(target, ModuleDefinition.APPS));
    }

    private UUID createRecord(UUID customAppId, Map<UUID, JsonNode> values) {
        Map<String, JsonNode> wire = new java.util.LinkedHashMap<>();
        values.forEach((propertyId, node) -> wire.put(propertyId.toString(), node));
        return customAppRecordService.create(customAppId, new CustomAppRecordRequest(wire)).id();
    }

    private List<UUID> searchIds(UUID customAppId, List<CustomAppValueFilterCriteria> filters,
                                 List<CustomAppValueSortCriteria> sorts) {
        Page<com.ibrhalil.forgesys.dto.CustomAppRecordResponse> page =
                customAppRecordService.search(customAppId, new CustomAppRecordSearchRequest(0, 50, sorts, filters));
        return page.getContent().stream()
                .map(com.ibrhalil.forgesys.dto.CustomAppRecordResponse::id)
                .toList();
    }

    private CustomAppValueFilterCriteria filter(CustomAppPropertyResponse property, CustomAppValueOperator operator, JsonNode value) {
        return new CustomAppValueFilterCriteria(property.id().toString(), operator, value);
    }

    private JsonNode json(String raw) {
        return objectMapper.readTree(raw);
    }

    private <T> T inTenant(java.util.function.Supplier<T> work) {
        return inTenant(schemaName, work);
    }

    private <T> T inTenant(String schema, java.util.function.Supplier<T> work) {
        TenantContext.setCurrentTenant(schema);
        try {
            return work.get();
        } finally {
            TenantContext.clear();
        }
    }

    private <T> T inTenantOf(Company target, java.util.function.Supplier<T> work) {
        return inTenant(target.getSchemaName(), work);
    }

    private boolean coreHistoryContains(String scriptFragment) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT count(*) FROM " + schemaName + ".flyway_schema_history WHERE script LIKE '%"
                             + scriptFragment + "%'")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private boolean regclassExists(String qualifiedName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT to_regclass('" + qualifiedName + "') IS NOT NULL")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }
}
