package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.CustomApp;
import com.ibrhalil.forgesys.entity.CustomAppProperty;
import com.ibrhalil.forgesys.entity.CustomAppView;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.entity.ViewType;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CustomApp definition CRUD endpoints (K-15 / Epic 3.0.B): 401/403, paged list + detail,
 * create/update/delete, TOCTOU name conflict, property/view definition rules
 * (FORMULA deferral, BOARD anchor validation) and the sort whitelist. Write paths
 * resolve plan limits, so requests run inside a tenant context backed by a seeded
 * FREE subscription (schemaName {@code public} — H2's PUBLIC schema — so the JWT
 * tenant binding matches).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomAppControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired PlanRepository planRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired SubscriptionRepository subscriptionRepository;

    private Project defaultAppsProject;

    @BeforeEach
    void seedFreeSubscription() {
        Plan free = planRepository.findByKey("free").orElseGet(() -> {
            Plan plan = new Plan();
            plan.setKey("free");
            plan.setName("Free");
            plan.setRank(0);
            plan.setActive(true);
            return planRepository.save(plan);
        });
        Company company = new Company();
        company.setName("Apps Test " + UUID.randomUUID());
        company.setSubdomain("apps" + UUID.randomUUID().toString().substring(0, 8));
        company.setSchemaName("public");
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);

        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(free);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);

        // K-45: flat writes without an explicit projectId land in the default APPS container.
        defaultAppsProject = new Project();
        defaultAppsProject.setName("Genel-" + UUID.randomUUID());
        defaultAppsProject.setType(ProjectType.APPS);
        defaultAppsProject.setDefault(true);
        entityManager.persist(defaultAppsProject);
        entityManager.flush();
    }

    /** Performs the request as a tenant-context-bound user with the given authorities. */
    private ResultActions perform(MockHttpServletRequestBuilder builder, String... authorities) throws Exception {
        TenantContext.setCurrentTenant("public");
        try {
            return mockMvc.perform(builder.cookie(auth("admin@apps.test", authorities)));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/custom-apps"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/custom-apps").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void listReturnsAppsPaged() throws Exception {
        seedCustomApp("CRM");
        seedCustomApp("Inventory");

        perform(get("/api/v1/custom-apps"), "apps:customapp:read")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.data[?(@.name=='CRM')]").exists())
                .andExpect(jsonPath("$.data[?(@.name=='Inventory')]").exists());
    }

    @Test
    void listRejectsUnknownSortProperty() throws Exception {
        // 'icon' became a registered sortable column with K-49 — 'deletedAt' stays off
        // the whitelist (internal soft-delete stamp, never a sort target).
        perform(get("/api/v1/custom-apps").queryParam("sort", "deletedAt"), "apps:customapp:read")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void planLimitsReturnsActivePlanLimits() throws Exception {
        // Also proves the literal segment wins over the /{id} UUID mapping.
        perform(get("/api/v1/custom-apps/plan-limits"), "apps:customapp:read")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planKey").value("free"))
                .andExpect(jsonPath("$.planName").value("Free"))
                .andExpect(jsonPath("$.maxCustomApps").value(3))
                .andExpect(jsonPath("$.maxRecordsPerCustomApp").value(1000));
    }

    @Test
    void planLimitsForbiddenWithoutReadPermission() throws Exception {
        perform(get("/api/v1/custom-apps/plan-limits"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void createReturns201() throws Exception {
        perform(post("/api/v1/custom-apps").contentType(JSON)
                        .content("""
                                {"name":"CRM","description":"Sales pipeline","icon":"📊"}"""),
                        "apps:customapp:write")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("CRM"))
                .andExpect(jsonPath("$.icon").value("📊"))
                .andExpect(jsonPath("$.projectId").value(defaultAppsProject.getId().toString()))
                .andExpect(jsonPath("$.projectName").value(defaultAppsProject.getName()));
    }

    @Test
    void createForbiddenWithoutWritePermission() throws Exception {
        perform(post("/api/v1/custom-apps").contentType(JSON).content("""
                        {"name":"CRM"}"""),
                "apps:customapp:read")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void createDuplicateNameReturnsAppNameTaken() throws Exception {
        seedCustomApp("CRM");
        perform(post("/api/v1/custom-apps").contentType(JSON).content("""
                        {"name":"CRM"}"""),
                "apps:customapp:write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("custom_app_name_taken"));
    }

    @Test
    void getReturnsDetailWithPropertiesAndViews() throws Exception {
        CustomApp app = seedCustomApp("CRM");
        CustomAppProperty status = seedProperty(app.getId(), "Status", PropertyType.SELECT,
                "{\"options\":[\"open\",\"won\"]}", true, 0);
        CustomAppView view = seedView(app.getId(), "Pipeline", ViewType.BOARD,
                "{\"groupBy\":\"" + status.getId() + "\"}", 0);

        perform(get("/api/v1/custom-apps/" + app.getId()), "apps:customapp:read")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CRM"))
                .andExpect(jsonPath("$.createdDate").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.properties[0].name").value("Status"))
                .andExpect(jsonPath("$.properties[0].type").value("SELECT"))
                .andExpect(jsonPath("$.properties[0].config.options[0]").value("open"))
                .andExpect(jsonPath("$.properties[0].required").value(true))
                .andExpect(jsonPath("$.views[0].name").value("Pipeline"))
                .andExpect(jsonPath("$.views[0].type").value("BOARD"))
                .andExpect(jsonPath("$.views[0].config.groupBy").value(status.getId().toString()));
    }

    @Test
    void getUnknownAppReturns404() throws Exception {
        perform(get("/api/v1/custom-apps/" + UUID.randomUUID()), "apps:customapp:read")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void updateRenamesApp() throws Exception {
        CustomApp app = seedCustomApp("CRM");
        perform(put("/api/v1/custom-apps/" + app.getId()).contentType(JSON)
                        .content("""
                                {"name":"Sales CRM","description":"renamed"}"""),
                "apps:customapp:write")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sales CRM"))
                .andExpect(jsonPath("$.description").value("renamed"));
    }

    @Test
    void deleteReturns204AndHidesTheApp() throws Exception {
        CustomApp app = seedCustomApp("CRM");
        perform(delete("/api/v1/custom-apps/" + app.getId()), "apps:customapp:delete")
                .andExpect(status().isNoContent());
        perform(get("/api/v1/custom-apps/" + app.getId()), "apps:customapp:read")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void propertyEndpointsCreateUpdateDelete() throws Exception {
        CustomApp app = seedCustomApp("CRM");

        String body = perform(post("/api/v1/custom-apps/" + app.getId() + "/properties")
                        .contentType(JSON)
                        .content("""
                                {"name":"Priority","type":"SELECT","config":{"options":["high","low"]},"required":false,"position":0}"""),
                        "apps:customapp:write")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Priority"))
                .andExpect(jsonPath("$.config.options.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        perform(put("/api/v1/custom-apps/" + app.getId() + "/properties/" + id).contentType(JSON)
                        .content("""
                                {"name":"Prio","type":"SELECT","config":{"options":["high"]},"required":true,"position":1}"""),
                "apps:customapp:write")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Prio"))
                .andExpect(jsonPath("$.required").value(true));

        perform(delete("/api/v1/custom-apps/" + app.getId() + "/properties/" + id), "apps:customapp:write")
                .andExpect(status().isNoContent());
    }

    @Test
    void propertyUpdateWithoutTypeIsRejected() throws Exception {
        CustomApp app = seedCustomApp("CRM");
        CustomAppProperty property = seedProperty(app.getId(), "Priority", PropertyType.TEXT, null, false, 0);

        // Wire contract: `type` is @NotNull on PUT too — clients must re-send the
        // unchanged type (immutability is a service-level same-type rule).
        perform(put("/api/v1/custom-apps/" + app.getId() + "/properties/" + property.getId()).contentType(JSON)
                        .content("""
                                {"name":"Prio","required":true}"""),
                "apps:customapp:write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.fields[0].field").value("type"));
    }

    @Test
    void propertyCreateAutoAssignsSequentialPositions() throws Exception {
        CustomApp app = seedCustomApp("CRM");

        perform(post("/api/v1/custom-apps/" + app.getId() + "/properties").contentType(JSON)
                        .content("""
                                {"name":"Title","type":"TEXT"}"""),
                "apps:customapp:write")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(0));

        perform(post("/api/v1/custom-apps/" + app.getId() + "/properties").contentType(JSON)
                        .content("""
                                {"name":"Status","type":"TEXT"}"""),
                "apps:customapp:write")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void propertyUpdateWithoutPositionKeepsCurrent() throws Exception {
        CustomApp app = seedCustomApp("CRM");
        CustomAppProperty property = seedProperty(app.getId(), "Priority", PropertyType.TEXT, null, false, 3);

        perform(put("/api/v1/custom-apps/" + app.getId() + "/properties/" + property.getId()).contentType(JSON)
                        .content("""
                                {"name":"Prio","type":"TEXT"}"""),
                "apps:customapp:write")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Prio"))
                .andExpect(jsonPath("$.position").value(3));
    }

    @Test
    void viewCreateAutoAssignsSequentialPositions() throws Exception {
        CustomApp app = seedCustomApp("CRM");

        perform(post("/api/v1/custom-apps/" + app.getId() + "/views").contentType(JSON)
                        .content("""
                                {"name":"Table","type":"TABLE"}"""),
                "apps:customapp:write")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(0));

        perform(post("/api/v1/custom-apps/" + app.getId() + "/views").contentType(JSON)
                        .content("""
                                {"name":"List","type":"LIST"}"""),
                "apps:customapp:write")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void propertyCreateRejectsDeferredFormulaType() throws Exception {
        CustomApp app = seedCustomApp("CRM");
        perform(post("/api/v1/custom-apps/" + app.getId() + "/properties").contentType(JSON)
                        .content("""
                                {"name":"Fx","type":"FORMULA"}"""),
                "apps:customapp:write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("custom_app_property_type_invalid"));
    }

    @Test
    void propertyCreateRejectsSelectWithoutOptions() throws Exception {
        CustomApp app = seedCustomApp("CRM");
        perform(post("/api/v1/custom-apps/" + app.getId() + "/properties").contentType(JSON)
                        .content("""
                                {"name":"Status","type":"SELECT"}"""),
                "apps:customapp:write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("custom_app_property_config_invalid"));
    }

    @Test
    void viewCreateRejectsBoardWithoutGroupBy() throws Exception {
        CustomApp app = seedCustomApp("CRM");
        perform(post("/api/v1/custom-apps/" + app.getId() + "/views").contentType(JSON)
                        .content("""
                                {"name":"Board","type":"BOARD"}"""),
                "apps:customapp:write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("custom_app_view_config_invalid"));
    }

    @Test
    void viewCreateWithSelectGroupBySucceeds() throws Exception {
        CustomApp app = seedCustomApp("CRM");
        CustomAppProperty status = seedProperty(app.getId(), "Status", PropertyType.SELECT,
                "{\"options\":[\"open\",\"won\"]}", false, 0);

        perform(post("/api/v1/custom-apps/" + app.getId() + "/views").contentType(JSON)
                        .content("""
                                {"name":"Board","type":"BOARD","config":{"groupBy":"%s"},"position":0}"""
                                .formatted(status.getId())),
                "apps:customapp:write")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config.groupBy").value(status.getId().toString()));
    }

    @Test
    void propertyOfAnotherAppIs404() throws Exception {
        CustomApp a = seedCustomApp("A");
        CustomApp b = seedCustomApp("B");
        CustomAppProperty propertyInB = seedProperty(b.getId(), "Status", PropertyType.TEXT, null, false, 0);

        perform(delete("/api/v1/custom-apps/" + a.getId() + "/properties/" + propertyInB.getId()), "apps:customapp:write")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    // --- helpers ---------------------------------------------------------

    private CustomApp seedCustomApp(String name) {
        CustomApp app = new CustomApp();
        app.setName(name);
        app.setProjectId(defaultAppsProject.getId());
        entityManager.persist(app);
        entityManager.flush();
        return app;
    }

    private CustomAppProperty seedProperty(UUID customAppId, String name, PropertyType type,
                                     String config, boolean required, int position) {
        CustomAppProperty property = new CustomAppProperty();
        property.setCustomAppId(customAppId);
        property.setName(name);
        property.setType(type);
        property.setConfig(config);
        property.setRequired(required);
        property.setPosition(position);
        entityManager.persist(property);
        entityManager.flush();
        return property;
    }

    private CustomAppView seedView(UUID customAppId, String name, ViewType type, String config, int position) {
        CustomAppView view = new CustomAppView();
        view.setCustomAppId(customAppId);
        view.setName(name);
        view.setType(type);
        view.setConfig(config);
        view.setPosition(position);
        entityManager.persist(view);
        entityManager.flush();
        return view;
    }
}
