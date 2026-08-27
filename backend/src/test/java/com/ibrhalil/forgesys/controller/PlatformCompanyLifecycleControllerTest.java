package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.config.PermissionCatalog;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.ModuleStatus;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.entity.TenantModule;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantModuleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-50 F4 lifecycle/report endpoints (subscription, modules, report). NOT
 * {@code @Transactional}: module activation runs in {@code REQUIRES_NEW} and its
 * inserts (permissions FK-less but committed, activation row FK -> company) must
 * see committed fixtures; committed rows are hard-deleted in {@code @AfterEach}
 * (plain SQL — ModuleControllerTest pattern).
 */
@SpringBootTest
@ActiveProfiles("test")
class PlatformCompanyLifecycleControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired
    PlanRepository planRepository;
    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    SubscriptionRepository subscriptionRepository;
    @Autowired
    TenantModuleRepository tenantModuleRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    ProjectRepository projectRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private Company company;
    private Plan freePlan;
    private Plan proPlan;
    private String schemaName;
    private final List<UUID> seededUserIds = new ArrayList<>();
    private final List<UUID> seededProjectIds = new ArrayList<>();

    @BeforeEach
    void seedFixtures() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        schemaName = "tenant_plf" + suffix;
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);

        freePlan = ensurePlan("free", 0);
        proPlan = ensurePlan("pro", 1);

        company = new Company();
        company.setName("Platform Lifecycle " + suffix);
        company.setSubdomain("plf" + suffix);
        company.setSchemaName(schemaName);
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);

        subscribe(company, freePlan);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        for (UUID projectId : seededProjectIds) {
            jdbcTemplate.update("DELETE FROM t_projects WHERE id = ?", projectId);
        }
        for (UUID userId : seededUserIds) {
            jdbcTemplate.update("DELETE FROM t_user_profiles WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM t_user_accounts WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM t_user_roles WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM t_user_groups WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM t_users WHERE id = ?", userId);
        }
        jdbcTemplate.update("DELETE FROM t_tenant_modules WHERE company_id = ?", company.getId());
        jdbcTemplate.update("DELETE FROM t_subscriptions WHERE company_id = ?", company.getId());
        jdbcTemplate.update("DELETE FROM t_companies WHERE id = ?", company.getId());
        for (ModuleDefinition def : ModuleDefinition.values()) {
            for (PermissionCatalog.PermissionDefinition permission : def.permissions()) {
                jdbcTemplate.update("DELETE FROM t_permissions WHERE name = ?", permission.name());
            }
        }
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
    }

    /* ── subscription ── */

    @Test
    void getSubscriptionReturnsCurrentPlan() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies/" + company.getId() + "/subscription")
                        .cookie(authPlatform(UUID.randomUUID(), "reader@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.planKey").value("free"))
                .andExpect(jsonPath("$.planName").value("Free"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getSubscriptionUnknownCompanyReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies/" + UUID.randomUUID() + "/subscription")
                        .cookie(authPlatform(UUID.randomUUID(), "reader@platform.test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void getSubscriptionForbiddenWithoutPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies/" + company.getId() + "/subscription")
                        .cookie(authPlatform(UUID.randomUUID(), "nobody@platform.test", List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void changePlanUpdatesPlan() throws Exception {
        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/subscription")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "ops@platform.test"))
                        .content("{\"planKey\":\"pro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planKey").value("pro"))
                .andExpect(jsonPath("$.planName").value("Pro"));
    }

    @Test
    void changePlanUnknownPlanReturns404() throws Exception {
        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/subscription")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "ops@platform.test"))
                        .content("{\"planKey\":\"ultra\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("plan_not_found"));
    }

    @Test
    void changePlanWithoutSubscriptionReturns409() throws Exception {
        jdbcTemplate.update("DELETE FROM t_subscriptions WHERE company_id = ?", company.getId());

        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/subscription")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "ops@platform.test"))
                        .content("{\"planKey\":\"pro\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("subscription_not_found"));
    }

    @Test
    void changePlanOnSuspendedCompanyReturns409() throws Exception {
        jdbcTemplate.update("UPDATE t_companies SET status = 'SUSPENDED' WHERE id = ?", company.getId());

        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/subscription")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "ops@platform.test"))
                        .content("{\"planKey\":\"pro\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("company_not_active"));
    }

    /** Scoped platform token without {@code platform:tenant:lifecycle} — 403 (F3 gate pattern). */
    @Test
    void changePlanForbiddenWithoutLifecyclePermission() throws Exception {
        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/subscription")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "reader@platform.test",
                                List.of("platform:company:read", "platform:tenant:report")))
                        .content("{\"planKey\":\"pro\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void changePlanRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/subscription")
                        .contentType(JSON)
                        .content("{\"planKey\":\"pro\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    /** A tenant JWT — even with the lifecycle authority — never reaches the platform surface (scope gate). */
    @Test
    void tenantJwtWithLifecycleAuthorityIsForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/subscription")
                        .contentType(JSON)
                        .cookie(auth("ops@tenant.test", "platform:tenant:lifecycle"))
                        .content("{\"planKey\":\"pro\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── modules ── */

    @Test
    void getModulesReturnsCatalogWithActivationState() throws Exception {
        seedModuleRow("pm");

        mockMvc.perform(get("/api/v1/platform/companies/" + company.getId() + "/modules")
                        .cookie(authPlatform(UUID.randomUUID(), "reader@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(ModuleDefinition.values().length)))
                .andExpect(jsonPath("$[?(@.key == 'pm')].active").value(true))
                .andExpect(jsonPath("$[?(@.key == 'pm')].allowedByPlan").value(true))
                .andExpect(jsonPath("$[?(@.key == 'apps')].active").value(false));
    }

    @Test
    void updateModulesActivatesPm() throws Exception {
        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/modules")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "ops@platform.test"))
                        .content("{\"activations\":[{\"key\":\"pm\",\"active\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'pm')].active").value(true));

        // Activation row landed (public schema, FK -> committed company).
        assertThatModuleActive("pm", true);
    }

    @Test
    void updateModulesDeactivatesPm() throws Exception {
        seedModuleRow("pm");

        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/modules")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "ops@platform.test"))
                        .content("{\"activations\":[{\"key\":\"pm\",\"active\":false}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'pm')].active").value(false));

        assertThatModuleActive("pm", false);
    }

    @Test
    void updateModulesUnknownKeyReturns404() throws Exception {
        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/modules")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "ops@platform.test"))
                        .content("{\"activations\":[{\"key\":\"does-not-exist\",\"active\":true}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("module_not_found"));
    }

    @Test
    void updateModulesOnTerminatedCompanyReturns409() throws Exception {
        jdbcTemplate.update("UPDATE t_companies SET status = 'TERMINATED' WHERE id = ?", company.getId());

        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/modules")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "ops@platform.test"))
                        .content("{\"activations\":[{\"key\":\"pm\",\"active\":true}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("company_not_active"));
    }

    @Test
    void updateModulesForbiddenWithoutLifecyclePermission() throws Exception {
        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/modules")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "reader@platform.test",
                                List.of("platform:company:read")))
                        .content("{\"activations\":[{\"key\":\"pm\",\"active\":true}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void updateModulesEmptyListReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/platform/companies/" + company.getId() + "/modules")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "ops@platform.test"))
                        .content("{\"activations\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /* ── report ── */

    /**
     * H2 fallback: the counts run inside the tenant window but resolve to PUBLIC
     * tables (no per-tenant tables on H2) — assert against seeded PUBLIC rows
     * with lower bounds (shared cached context accumulates other tests' rows).
     */
    @Test
    void getReportReturnsCounters() throws Exception {
        seedReportUser("report_a" + System.nanoTime());
        seedReportUser("report_b" + System.nanoTime());
        seedReportProject("Report Project " + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/platform/companies/" + company.getId() + "/report")
                        .cookie(authPlatform(UUID.randomUUID(), "analyst@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.userCount", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.projectCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.customAppCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.noteCount", greaterThanOrEqualTo(0)));
    }

    @Test
    void getReportUnknownCompanyReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies/" + UUID.randomUUID() + "/report")
                        .cookie(authPlatform(UUID.randomUUID(), "analyst@platform.test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    /** Scoped platform token without {@code platform:tenant:report} — 403. */
    @Test
    void getReportForbiddenWithoutReportPermission() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies/" + company.getId() + "/report")
                        .cookie(authPlatform(UUID.randomUUID(), "reader@platform.test",
                                List.of("platform:company:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── helpers ── */

    private Plan ensurePlan(String key, int rank) {
        return planRepository.findByKey(key).orElseGet(() -> {
            Plan plan = new Plan();
            plan.setKey(key);
            plan.setName(key.equals("free") ? "Free" : key.equals("pro") ? "Pro" : key);
            plan.setRank(rank);
            plan.setActive(true);
            return planRepository.save(plan);
        });
    }

    private void subscribe(Company target, Plan plan) {
        Subscription subscription = new Subscription();
        subscription.setCompany(target);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);
    }

    private void seedModuleRow(String moduleKey) {
        TenantModule row = new TenantModule();
        row.setCompany(company);
        row.setModuleKey(moduleKey);
        row.setStatus(ModuleStatus.ACTIVE);
        row.setActivatedAt(OffsetDateTime.now());
        tenantModuleRepository.save(row);
    }

    /** Resolved against the live (non-deleted) activation rows — soft-deleted rows are filtered. */
    private void assertThatModuleActive(String moduleKey, boolean expected) {
        boolean active = tenantModuleRepository
                .findByCompanyIdAndModuleKey(company.getId(), moduleKey)
                .map(row -> row.getStatus() == ModuleStatus.ACTIVE)
                .orElse(false);
        org.assertj.core.api.Assertions.assertThat(active).isEqualTo(expected);
    }

    private void seedReportUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@report.test");
        user.setPassword("$2a$12$dummyHashForTestingOnly00000000000000000000000000000");
        user.setEmailVerified(false);
        UserAccount account = new UserAccount();
        account.setUser(user);
        user.setUserAccount(account);
        User saved = userRepository.save(user);
        seededUserIds.add(saved.getId());
    }

    private void seedReportProject(String name) {
        Project project = new Project();
        project.setName(name);
        project.setType(ProjectType.TASKS);
        Project saved = projectRepository.save(project);
        seededProjectIds.add(saved.getId());
    }
}
