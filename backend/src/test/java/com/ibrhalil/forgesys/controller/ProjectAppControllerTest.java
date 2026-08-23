package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.App;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Custom apps nested under their APPS-type collection container (K-45 step 5) — the
 * TaskController pattern: 404 unknown container, 409 {@code project_type_mismatch}
 * for a non-APPS one, container scoping, the flat-create default fallback, and the
 * type-change lock now that an APPS project can hold apps.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectAppControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired PlanRepository planRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired SubscriptionRepository subscriptionRepository;

    private Project appsProject;

    @BeforeEach
    void seedFixtures() {
        Plan free = planRepository.findByKey("free").orElseGet(() -> {
            Plan plan = new Plan();
            plan.setKey("free");
            plan.setName("Free");
            plan.setRank(0);
            plan.setActive(true);
            return planRepository.save(plan);
        });
        Company company = new Company();
        company.setName("ProjApp Test " + UUID.randomUUID());
        company.setSubdomain("projapp" + UUID.randomUUID().toString().substring(0, 8));
        company.setSchemaName("public");
        company.setStatus(CompanyStatus.ACTIVE);
        companyRepository.save(company);

        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(free);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);

        appsProject = new Project();
        appsProject.setName("Apps Home " + UUID.randomUUID());
        appsProject.setType(ProjectType.APPS);
        entityManager.persist(appsProject);
        entityManager.flush();
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + appsProject.getId() + "/apps"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + appsProject.getId() + "/apps")
                        .cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void listInUnknownProjectReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/apps")
                        .cookie(auth("reader@tenant.test", "apps:app:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void listInNonAppsProjectReturns409() throws Exception {
        Project notes = seedProject(ProjectType.NOTES, "Notes");

        mockMvc.perform(get("/api/v1/projects/" + notes.getId() + "/apps")
                        .cookie(auth("reader@tenant.test", "apps:app:read")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_type_mismatch"));
    }

    /** Performs the request as a tenant-context-bound user (plan-limit chain needs a tenant). */
    private org.springframework.test.web.servlet.ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String... authorities) throws Exception {
        com.ibrhalil.forgesys.common.tenant.TenantContext.setCurrentTenant("public");
        try {
            return mockMvc.perform(builder.cookie(auth("writer@apps.test", authorities)));
        } finally {
            com.ibrhalil.forgesys.common.tenant.TenantContext.clear();
        }
    }

    @Test
    void listScopesToItsContainer() throws Exception {
        Project other = seedProject(ProjectType.APPS, "Other Apps");
        seedApp(appsProject.getId(), "CRM");
        seedApp(other.getId(), "Inventory");

        mockMvc.perform(get("/api/v1/projects/" + appsProject.getId() + "/apps")
                        .cookie(auth("reader@tenant.test", "apps:app:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value(org.hamcrest.Matchers.containsString("CRM")))
                .andExpect(jsonPath("$.data[0].projectName").value(appsProject.getName()));
    }

    @Test
    void flatListFiltersByProjectId() throws Exception {
        Project other = seedProject(ProjectType.APPS, "Other Apps");
        seedApp(appsProject.getId(), "CRM");
        seedApp(other.getId(), "Inventory");

        mockMvc.perform(get("/api/v1/apps")
                        .param("projectId", other.getId().toString())
                        .cookie(auth("reader@tenant.test", "apps:app:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value(org.hamcrest.Matchers.containsString("Inventory")));
    }

    @Test
    void createReturns201AndAnchorsToContainer() throws Exception {
        perform(post("/api/v1/projects/" + appsProject.getId() + "/apps")
                        .contentType(JSON)
                        .content("""
                                {"name":"CRM","icon":"📊"}"""),
                        "apps:app:write")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("CRM"))
                .andExpect(jsonPath("$.projectId").value(appsProject.getId().toString()));
    }

    @Test
    void createInNonAppsProjectReturns409() throws Exception {
        Project notes = seedProject(ProjectType.NOTES, "Notes");

        mockMvc.perform(post("/api/v1/projects/" + notes.getId() + "/apps")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "apps:app:write"))
                        .content("""
                                {"name":"CRM"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_type_mismatch"));
    }

    @Test
    void flatCreateWithoutProjectDefaultsToDefaultContainer() throws Exception {
        Project def = new Project();
        def.setName("Genel-" + UUID.randomUUID());
        def.setType(ProjectType.APPS);
        def.setDefault(true);
        entityManager.persist(def);
        entityManager.flush();

        perform(post("/api/v1/apps").contentType(JSON)
                        .content("""
                                {"name":"Top nav app"}"""),
                "apps:app:write")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(def.getId().toString()));
    }

    @Test
    void updateMovesAppBetweenAppsContainers() throws Exception {
        Project other = seedProject(ProjectType.APPS, "Other Apps");
        App app = seedApp(appsProject.getId(), "CRM");

        mockMvc.perform(put("/api/v1/apps/" + app.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "apps:app:write"))
                        .content("""
                                {"name":"CRM","projectId":"%s"}""".formatted(other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(other.getId().toString()))
                .andExpect(jsonPath("$.projectName").value(other.getName()));
    }

    @Test
    void updateRejectsMoveToNonAppsContainer() throws Exception {
        Project notes = seedProject(ProjectType.NOTES, "Notes");
        App app = seedApp(appsProject.getId(), "CRM");

        mockMvc.perform(put("/api/v1/apps/" + app.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "apps:app:write"))
                        .content("""
                                {"name":"CRM","projectId":"%s"}""".formatted(notes.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_type_mismatch"));
    }

    @Test
    void typeChangeWithAppsReturns409() throws Exception {
        seedApp(appsProject.getId(), "CRM");

        mockMvc.perform(put("/api/v1/projects/" + appsProject.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"%s","type":"TASKS"}""".formatted(appsProject.getName())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_type_change_forbidden"));
    }

    private Project seedProject(ProjectType type, String name) {
        Project project = new Project();
        project.setName(name + " " + UUID.randomUUID());
        project.setType(type);
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    private App seedApp(UUID projectId, String name) {
        App app = new App();
        app.setName(name + "-" + UUID.randomUUID().toString().substring(0, 8));
        app.setProjectId(projectId);
        entityManager.persist(app);
        entityManager.flush();
        return app;
    }
}
