package com.ibrhalil.forgesys;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.service.TenantMigrationSupport;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-tenant isolation against a real PostgreSQL ([RISK-20], Faz B). The H2 test suite
 * runs on a single {@code public} schema with {@code TenantContext} unset, so the
 * {@code SET search_path} mechanism — the backbone of schema-per-tenant isolation — is
 * never exercised there. This class provisions two real tenant schemas in PostgreSQL via
 * Testcontainers and asserts that data in one tenant is invisible from the other.
 *
 * <p>It also validates [RISK-26]: the two-phase provisioning flow must land the admin
 * user in the correct tenant schema (the REQUIRES_NEW mid-transaction context switch).
 *
 * <p><strong>Gated:</strong> skipped unless {@code -Dforgesys.pg.it=true} is set, so the
 * default Docker-free build ({@code mvn clean install}) stays green. Run with:
 * <pre>{@code
 * ./mvnw -pl backend test -Dtest=CrossTenantIsolationTest -Dforgesys.pg.it=true
 * }</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "forgesys.pg.it", matches = "true")
@Import(CrossTenantIsolationTest.ContainerConfig.class)
class CrossTenantIsolationTest {

    private static final String TENANT_A_SCHEMA = "tenant_tenanta";
    private static final String TENANT_B_SCHEMA = "tenant_tenantb";
    private static final String ADMIN_A_EMAIL = "admin@tenanta.test";

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    /**
     * Switches the {@code test} profile (H2 + create-drop + flyway off) to real PostgreSQL
     * managed by the container: dialect, {@code ddl-auto=none} (schema lives in Flyway),
     * and public-schema Flyway migrations so {@code t_companies}/tokens exist at startup.
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.dialect", () -> PostgreSQLDialect.class.getName());
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/public");
    }

    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private TenantMigrationSupport tenantMigrationSupport;
    @Autowired private TenantProvisioningService provisioningService;
    @Autowired private InMemoryMailSender mailSender;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void provisionTenants() {
        // K-16: provisioning now writes an initial FREE subscription + default module
        // activations, so the plan rows must exist (PlanSyncRunner's job at runtime;
        // absent in the test profile).
        for (PlanDefinition definition : PlanDefinition.values()) {
            Plan plan = planRepository.findByKey(definition.key()).orElseGet(Plan::new);
            plan.setKey(definition.key());
            plan.setName(definition.displayName());
            plan.setRank(definition.rank());
            plan.setActive(true);
            planRepository.save(plan);
        }
        // Tenant A via the full two-phase signup flow (also exercises RISK-26): the
        // admin user must land in tenant_tenanta despite the mid-transaction
        // TenantContext switch.
        TenantProvisioningTestSupport.provisionViaTwoPhaseFlow(provisioningService, mailSender,
                new CompanyRegisterRequest(
                        "Tenant A", "tenanta", ADMIN_A_EMAIL, "Secret123!", "Admin", "A"));
        // Tenant B via the lower-level path (CREATE SCHEMA + programmatic tenant Flyway).
        seedActiveTenant("tenantb");
    }

    /**
     * [RISK-26 + RISK-20] The admin user created by the bootstrap flow lives in
     * {@code tenant_tenanta} and is invisible from {@code tenant_tenantb}.
     */
    @Test
    void tenantAAdminIsInvisibleFromTenantB() {
        inTenant(TENANT_A_SCHEMA, () -> assertThat(userRepository.findByEmail(ADMIN_A_EMAIL)).isPresent());
        inTenant(TENANT_B_SCHEMA, () -> assertThat(userRepository.findByEmail(ADMIN_A_EMAIL)).isEmpty());
    }

    /**
     * [RISK-20] Data written in {@code tenant_tenantb} is invisible from
     * {@code tenant_tenanta} (isolation is bidirectional).
     */
    @Test
    void tenantBDataIsInvisibleFromTenantA() {
        inTenant(TENANT_B_SCHEMA, () -> userRepository.save(newUser("bob@tenantb.test", "bob")));
        inTenant(TENANT_A_SCHEMA, () -> assertThat(userRepository.findByEmail("bob@tenantb.test")).isEmpty());
        inTenant(TENANT_B_SCHEMA, () -> assertThat(userRepository.findByEmail("bob@tenantb.test")).isPresent());
    }

    // --- helpers ---------------------------------------------------------

    private void seedActiveTenant(String subdomain) {
        String schema = "tenant_" + subdomain;
        TenantContext.clear();
        Company company = new Company();
        company.setName(subdomain);
        company.setSubdomain(subdomain);
        company.setSchemaName(schema);
        company.setStatus(CompanyStatus.ACTIVE);
        companyRepository.save(company);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create tenant schema: " + schema, e);
        }
        tenantMigrationSupport.migrateSchema(schema);
    }

    private static User newUser(String email, String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("$2a$12$placeholderNotUsedForLoginHere000000000000000000");
        user.setEmailVerified(true);
        UserAccount account = new UserAccount();
        account.setUser(user);
        user.setUserAccount(account);
        return user;
    }

    /** Runs a tenant-scoped action with the context set, always clearing it afterward. */
    private void inTenant(String schema, Runnable action) {
        TenantContext.setCurrentTenant(schema);
        try {
            action.run();
        } finally {
            TenantContext.clear();
        }
    }
}
