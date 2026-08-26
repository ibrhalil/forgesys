package com.ibrhalil.forgesys;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.config.PlatformPermissionCatalog;
import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.dto.PlatformSwitchExchangeRequest;
import com.ibrhalil.forgesys.dto.PlatformSwitchStartResponse;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.PlatformUser;
import com.ibrhalil.forgesys.entity.PlatformUserType;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import com.ibrhalil.forgesys.service.TenantProvisioningService;
import com.ibrhalil.forgesys.service.mail.InMemoryMailSender;
import com.ibrhalil.forgesys.tenant.TenantFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.hibernate.dialect.PostgreSQLDialect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-50 F6: the real two-tenant switch flow against PostgreSQL — the H2 suite cannot
 * exercise it (single schema, no {@code SET search_path}). Provisions tenants A and C
 * through the production two-phase flow ({@code TenantProvisioningTestSupport}, RISK-26),
 * then drives the full HTTP chain (TenantFilter + security) with subdomain Host headers:
 * platform start → tenant-host exchange → impersonation honored ONLY on the target
 * tenant, rejected on the third tenant (RISK-19 symmetry), isolation intact, logout ends
 * the session.
 *
 * <p><strong>Gated:</strong> skipped unless {@code -Dforgesys.pg.it=true}. Run with:
 * <pre>{@code
 * ./mvnw -pl backend test -Dtest=PlatformSwitchImpersonationIT -Dforgesys.pg.it=true
 * }</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "forgesys.pg.it", matches = "true")
@Import(PlatformSwitchImpersonationIT.ContainerConfig.class)
class PlatformSwitchImpersonationIT {

    private static final String TENANT_A_SCHEMA = "tenant_tenanta";
    private static final String TENANT_C_SCHEMA = "tenant_tenantc";
    private static final String ADMIN_A_EMAIL = "admin@tenanta.test";
    private static final String PLATFORM_EMAIL = "root@platform.test";
    private static final String PLATFORM_DISPLAY = "Root Admin";

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
        // them into jsonb columns (the switch flow's tenant-side audit writes).
        registry.add("spring.datasource.hikari.data-source-properties.stringtype", () -> "unspecified");
    }

    @Autowired private WebApplicationContext context;
    @Autowired private TenantFilter tenantFilter;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private PlatformUserRepository platformUserRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantProvisioningService provisioningService;
    @Autowired private InMemoryMailSender mailSender;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private UUID platformUserId;
    private Company companyA;

    @BeforeAll
    void provisionTenantsAndPlatformIdentity() {
        for (PlanDefinition definition : PlanDefinition.values()) {
            Plan plan = planRepository.findByKey(definition.key()).orElseGet(Plan::new);
            plan.setKey(definition.key());
            plan.setName(definition.displayName());
            plan.setRank(definition.rank());
            plan.setActive(true);
            planRepository.save(plan);
        }
        TenantProvisioningTestSupport.provisionViaTwoPhaseFlow(provisioningService, mailSender,
                new CompanyRegisterRequest("Tenant A", "tenanta", ADMIN_A_EMAIL, "Secret123!", "Admin", "A"));
        TenantProvisioningTestSupport.provisionViaTwoPhaseFlow(provisioningService, mailSender,
                new CompanyRegisterRequest("Tenant C", "tenantc", "admin@tenantc.test", "Secret123!", "Admin", "C"));
        // RbacSeeder is absent in the test profile — grant tenant A's admin an
        // all_permissions role by hand (the impersonation-target fixture).
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        inTenant(TENANT_A_SCHEMA, () -> transactionTemplate.executeWithoutResult(tx -> {
            Role adminRole = new Role();
            adminRole.setName("Admin");
            adminRole.setAllPermissions(true);
            roleRepository.save(adminRole);
            User admin = userRepository.findByEmail(ADMIN_A_EMAIL).orElseThrow();
            admin.getRoles().add(adminRole);
            userRepository.save(admin);
        }));

        PlatformUser platformUser = new PlatformUser();
        platformUser.setEmail(PLATFORM_EMAIL);
        platformUser.setDisplayName(PLATFORM_DISPLAY);
        platformUser.setUserType(PlatformUserType.HUMAN);
        platformUser.setEnabled(true);
        platformUserRepository.save(platformUser);
        platformUserId = platformUser.getId();

        companyA = companyRepository.findBySubdomain("tenanta").orElseThrow();
    }

    @BeforeEach
    void buildMockMvc() {
        Filter securityFilter = (Filter) context.getBean("springSecurityFilterChain");
        // TenantFilter BEFORE the security chain — mirrors the production registration order.
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(tenantFilter, securityFilter)
                .build();
    }

    @Test
    void switchFlowHonorsOnlyTheTargetTenant() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/platform/companies/{id}/switch", companyA.getId())
                        .cookie(platformCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"support investigation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUrl").value("http://tenanta.localhost:3000"))
                .andReturn();
        String code = objectMapper.readValue(start.getResponse().getContentAsString(),
                PlatformSwitchStartResponse.class).switchCode();

        Cookie imp = exchangeOnHost("tenanta.localhost", code);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.HOST, "tenanta.localhost").cookie(imp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_A_EMAIL))
                .andExpect(jsonPath("$.impersonation.actorId").value(platformUserId.toString()))
                .andExpect(jsonPath("$.impersonation.actorEmail").value(PLATFORM_DISPLAY));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.HOST, "tenantc.localhost").cookie(imp))
                .andExpect(status().isUnauthorized());

        inTenant(TENANT_C_SCHEMA, () ->
                assertThat(userRepository.findByEmail(ADMIN_A_EMAIL)).isEmpty());

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.HOST, "tenanta.localhost").cookie(imp))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.HOST, "tenanta.localhost").cookie(imp))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---------------------------------------------------------

    private Cookie platformCookie() {
        return new Cookie("sf_platform_access_token", jwtTokenProvider.generatePlatformAccessToken(
                platformUserId.toString(), PLATFORM_EMAIL, PlatformPermissionCatalog.ALL_NAMES));
    }

    private Cookie exchangeOnHost(String host, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/platform-switch")
                        .header(HttpHeaders.HOST, host)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlatformSwitchExchangeRequest(code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("sf_access_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private void inTenant(String schema, Runnable action) {
        TenantContext.setCurrentTenant(schema);
        try {
            action.run();
        } finally {
            TenantContext.clear();
        }
    }
}
