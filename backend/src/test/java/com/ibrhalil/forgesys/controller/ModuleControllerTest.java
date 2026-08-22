package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module endpoints (K-16 / Epic 3.0.A). NOT {@code @Transactional}: activation runs in
 * {@code REQUIRES_NEW} and its inserts (FK -&gt; company) must see committed fixtures;
 * committed rows are hard-deleted in {@code @AfterEach} (plain SQL — soft-deleted
 * permission rows would trip H2's plain UNIQUE(name) on re-runs).
 */
@SpringBootTest
@ActiveProfiles("test")
class ModuleControllerTest extends AbstractRbacWebTest {

    @Autowired
    PlanRepository planRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Company company;
    private Plan freePlan;
    private String schemaName;

    @BeforeEach
    void seedFixtures() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        schemaName = "tenant_mod" + suffix;
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);

        freePlan = planRepository.findByKey("free").orElseGet(() -> {
            Plan plan = new Plan();
            plan.setKey("free");
            plan.setName("Free");
            plan.setRank(0);
            plan.setActive(true);
            return planRepository.save(plan);
        });

        company = new Company();
        company.setName("Module Test " + suffix);
        company.setSubdomain("mod" + suffix);
        company.setSchemaName(schemaName);
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        // Hard deletes (plain SQL): committed activation rows + module permission rows,
        // in FK-safe order. Soft-deleted permissions would block re-seeding on H2.
        jdbcTemplate.update("DELETE FROM t_tenant_modules WHERE company_id = ?", company.getId());
        jdbcTemplate.update("DELETE FROM t_subscriptions WHERE company_id = ?", company.getId());
        jdbcTemplate.update("DELETE FROM t_companies WHERE id = ?", company.getId());
        for (ModuleDefinition def : ModuleDefinition.values()) {
            for (com.ibrhalil.forgesys.config.PermissionCatalog.PermissionDefinition permission : def.permissions()) {
                jdbcTemplate.update("DELETE FROM t_permissions WHERE name = ?", permission.name());
            }
        }
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
    }

    private void subscribe(Company target, Plan plan) {
        Subscription subscription = new Subscription();
        subscription.setCompany(target);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);
    }

    /** Performs a request inside the seeded company's tenant context. */
    private org.springframework.test.web.servlet.ResultActions performAsTenant(MockHttpServletRequestBuilder builder)
            throws Exception {
        TenantContext.setCurrentTenant(schemaName);
        try {
            return mockMvc.perform(builder.cookie(authTenant(schemaName, "admin@" + company.getSubdomain() + ".test",
                    "iam:module:read", "iam:module:write")));
        } finally {
            TenantContext.clear();
        }
    }

    /* ── GET /api/v1/modules ── */

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/modules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutPermission() throws Exception {
        TenantContext.setCurrentTenant(schemaName);
        try {
            mockMvc.perform(get("/api/v1/modules").cookie(authTenant(schemaName, "nop@" + company.getSubdomain() + ".test")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("auth_access_denied"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listWithoutTenantContextIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/modules").cookie(auth("reader@tenant.test", "iam:module:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("tenant_not_found"));
    }

    @Test
    void listShowsCatalogWithActivationState() throws Exception {
        subscribe(company, freePlan);

        performAsTenant(get("/api/v1/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("pm"))
                .andExpect(jsonPath("$[0].name").value("Projects & Tasks"))
                .andExpect(jsonPath("$[0].minPlan").value("free"))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].allowedByPlan").value(true));
    }

    @Test
    void listWithoutSubscriptionShowsNotAllowedByPlan() throws Exception {
        performAsTenant(get("/api/v1/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("pm"))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].allowedByPlan").value(false));
    }

    /* ── POST /api/v1/modules/{key}/activate ── */

    @Test
    void activateRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/modules/pm/activate"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void activateForbiddenWithoutPermission() throws Exception {
        TenantContext.setCurrentTenant(schemaName);
        try {
            mockMvc.perform(post("/api/v1/modules/pm/activate")
                            .cookie(authTenant(schemaName, "nop@" + company.getSubdomain() + ".test")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("auth_access_denied"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void activateUnknownModuleReturns404() throws Exception {
        performAsTenant(post("/api/v1/modules/does-not-exist/activate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("module_not_found"));
    }

    @Test
    void activateWithoutSubscriptionReturns409() throws Exception {
        performAsTenant(post("/api/v1/modules/pm/activate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("subscription_not_found"));
    }

    @Test
    void activateActivatesModuleAndIsIdempotent() throws Exception {
        subscribe(company, freePlan);

        performAsTenant(post("/api/v1/modules/pm/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("pm"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.allowedByPlan").value(true));

        // Second call is idempotent — still 200, no duplicate row / error.
        performAsTenant(post("/api/v1/modules/pm/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("pm"))
                .andExpect(jsonPath("$.active").value(true));

        performAsTenant(get("/api/v1/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true));
    }
}
