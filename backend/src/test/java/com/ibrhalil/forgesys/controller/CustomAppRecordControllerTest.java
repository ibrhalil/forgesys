package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.CustomApp;
import com.ibrhalil.forgesys.entity.CustomAppProperty;
import com.ibrhalil.forgesys.entity.CustomAppRecord;
import com.ibrhalil.forgesys.entity.CustomAppRecordValue;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.PropertyType;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Record CRUD endpoints (K-15 / Epic 3.0.B): 401/403, create with typed values
 * (required coverage, unknown property, type mismatch), PATCH merge semantics (partial
 * update, clearing an optional value), app-scoped isolation (a record of app B is
 * unreachable through app A) and delete. The JSONB search endpoint is PostgreSQL-only
 * and covered by the gated {@code CustomAppIT}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomAppRecordControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired PlanRepository planRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired SubscriptionRepository subscriptionRepository;

    private CustomApp app;
    private CustomAppProperty name;
    private CustomAppProperty amount;
    private CustomAppProperty stage;
    private Project defaultAppsProject;

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
        company.setName("Rec Test " + UUID.randomUUID());
        company.setSubdomain("rec" + UUID.randomUUID().toString().substring(0, 8));
        company.setSchemaName("public");
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);

        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(free);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);

        // K-45: apps live in an APPS container; record tests don't care which one.
        defaultAppsProject = new Project();
        defaultAppsProject.setName("Apps " + UUID.randomUUID());
        defaultAppsProject.setType(ProjectType.APPS);
        entityManager.persist(defaultAppsProject);
        entityManager.flush();

        app = seedCustomApp("CRM");
        name = seedProperty(app.getId(), "Name", PropertyType.TEXT, null, true, 0);
        amount = seedProperty(app.getId(), "Amount", PropertyType.NUMBER, null, false, 1);
        stage = seedProperty(app.getId(), "Stage", PropertyType.SELECT,
                "{\"options\":[\"open\",\"won\"]}", false, 2);
    }

    /** Performs the request as a tenant-context-bound user with the given authorities. */
    private ResultActions perform(MockHttpServletRequestBuilder builder, String... authorities) throws Exception {
        TenantContext.setCurrentTenant("public");
        try {
            return mockMvc.perform(builder.cookie(auth("editor@apps.test", authorities)));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/custom-apps/" + app.getId() + "/records"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/custom-apps/" + app.getId() + "/records").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void createReturns201WithValues() throws Exception {
        perform(post("/api/v1/custom-apps/" + app.getId() + "/records").contentType(JSON)
                        .content(recordBody("""
                                "%s":"Acme","%s":1500,"%s":"open\""""
                                .formatted(name.getId(), amount.getId(), stage.getId()))),
                "apps:record:write")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customAppId").value(app.getId().toString()))
                .andExpect(jsonPath("$.values.%s".formatted(name.getId())).value("Acme"))
                .andExpect(jsonPath("$.values.%s".formatted(amount.getId())).value(1500))
                .andExpect(jsonPath("$.values.%s".formatted(stage.getId())).value("open"));
    }

    @Test
    void createMissingRequiredPropertyReturns400() throws Exception {
        perform(post("/api/v1/custom-apps/" + app.getId() + "/records").contentType(JSON)
                        .content(recordBody("\"%s\":1500".formatted(amount.getId()))),
                "apps:record:write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("custom_app_record_value_invalid"));
    }

    @Test
    void createUnknownPropertyReturns400() throws Exception {
        String unknown = UUID.randomUUID().toString();
        perform(post("/api/v1/custom-apps/" + app.getId() + "/records").contentType(JSON)
                        .content(recordBody("\"%s\":\"Acme\",\"%s\":\"x\""
                                .formatted(name.getId(), unknown))),
                "apps:record:write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("custom_app_record_value_invalid"));
    }

    @Test
    void createTypeMismatchReturns400() throws Exception {
        // NUMBER property fed a string, SELECT property fed an unknown option.
        perform(post("/api/v1/custom-apps/" + app.getId() + "/records").contentType(JSON)
                        .content(recordBody("\"%s\":\"Acme\",\"%s\":\"not-a-number\""
                                .formatted(name.getId(), amount.getId()))),
                "apps:record:write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("custom_app_record_value_invalid"));

        perform(post("/api/v1/custom-apps/" + app.getId() + "/records").contentType(JSON)
                        .content(recordBody("\"%s\":\"Acme\",\"%s\":\"bogus\""
                                .formatted(name.getId(), stage.getId()))),
                "apps:record:write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("custom_app_record_value_invalid"));
    }

    @Test
    void createInUnknownAppReturns404() throws Exception {
        perform(post("/api/v1/custom-apps/" + UUID.randomUUID() + "/records").contentType(JSON)
                        .content("""
                                {"values":{}}"""),
                "apps:record:write")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void patchPartiallyUpdatesAndClears() throws Exception {
        CustomAppRecord record = seedRecord(app.getId(), name, "Acme", amount, "1500");

        // Overwrite the name, keep the amount untouched (absent key).
        perform(patch("/api/v1/custom-apps/" + app.getId() + "/records/" + record.getId()).contentType(JSON)
                        .content(recordBody("\"%s\":\"Acme II\"".formatted(name.getId()))),
                "apps:record:write")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.%s".formatted(name.getId())).value("Acme II"))
                .andExpect(jsonPath("$.values.%s".formatted(amount.getId())).value(1500));

        // JSON null clears a non-required value.
        perform(patch("/api/v1/custom-apps/" + app.getId() + "/records/" + record.getId()).contentType(JSON)
                        .content(recordBody("\"%s\":null".formatted(amount.getId()))),
                "apps:record:write")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.%s".formatted(amount.getId())).doesNotExist());

        // JSON null on a REQUIRED property is rejected.
        perform(patch("/api/v1/custom-apps/" + app.getId() + "/records/" + record.getId()).contentType(JSON)
                        .content(recordBody("\"%s\":null".formatted(name.getId()))),
                "apps:record:write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("custom_app_record_value_invalid"));
    }

    @Test
    void getReturnsRecordWithValues() throws Exception {
        CustomAppRecord record = seedRecord(app.getId(), name, "Acme", amount, "1500");

        perform(get("/api/v1/custom-apps/" + app.getId() + "/records/" + record.getId()), "apps:record:read")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(record.getId().toString()))
                .andExpect(jsonPath("$.values.%s".formatted(name.getId())).value("Acme"))
                .andExpect(jsonPath("$.values.%s".formatted(amount.getId())).value(1500));
    }

    @Test
    void listReturnsRecordsPaged() throws Exception {
        seedRecord(app.getId(), name, "Acme", null, null);
        seedRecord(app.getId(), name, "Globex", null, null);

        perform(get("/api/v1/custom-apps/" + app.getId() + "/records"), "apps:record:read")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    void recordIsScopedToItsApp() throws Exception {
        CustomApp other = seedCustomApp("Inventory");
        CustomAppRecord recordInOther = seedRecord(other.getId(), name, "Acme", null, null);

        perform(get("/api/v1/custom-apps/" + app.getId() + "/records/" + recordInOther.getId()), "apps:record:read")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void deleteReturns204() throws Exception {
        CustomAppRecord record = seedRecord(app.getId(), name, "Acme", null, null);

        perform(delete("/api/v1/custom-apps/" + app.getId() + "/records/" + record.getId()), "apps:record:delete")
                .andExpect(status().isNoContent());
        perform(get("/api/v1/custom-apps/" + app.getId() + "/records/" + record.getId()), "apps:record:read")
                .andExpect(status().isNotFound());
    }

    // --- helpers ---------------------------------------------------------

    /** Wraps fragment pairs into a values object: {@code "key":val,...} → {@code {"values":{...}}}. */
    private static String recordBody(String valueEntries) {
        return "{\"values\":{" + valueEntries + "}}";
    }

    private CustomApp seedCustomApp(String appName) {
        CustomApp seeded = new CustomApp();
        seeded.setName(appName + "-" + UUID.randomUUID());
        seeded.setProjectId(defaultAppsProject.getId());
        entityManager.persist(seeded);
        entityManager.flush();
        return seeded;
    }

    private CustomAppProperty seedProperty(UUID customAppId, String propertyName, PropertyType type,
                                     String config, boolean required, int position) {
        CustomAppProperty property = new CustomAppProperty();
        property.setCustomAppId(customAppId);
        property.setName(propertyName + "-" + UUID.randomUUID());
        property.setType(type);
        property.setConfig(config);
        property.setRequired(required);
        property.setPosition(position);
        entityManager.persist(property);
        entityManager.flush();
        return property;
    }

    private CustomAppRecord seedRecord(UUID customAppId, CustomAppProperty requiredName, String nameValue,
                                 CustomAppProperty optionalAmount, String amountValue) {
        CustomAppRecord record = new CustomAppRecord();
        record.setCustomAppId(customAppId);
        entityManager.persist(record);
        if (requiredName != null && nameValue != null) {
            persistValue(record.getId(), requiredName.getId(), "\"" + nameValue.replace("\"", "\\\"") + "\"");
        }
        if (optionalAmount != null && amountValue != null) {
            persistValue(record.getId(), optionalAmount.getId(), amountValue);
        }
        entityManager.flush();
        return record;
    }

    private void persistValue(UUID recordId, UUID propertyId, String json) {
        CustomAppRecordValue value = new CustomAppRecordValue();
        value.setRecordId(recordId);
        value.setPropertyId(propertyId);
        value.setValue(json);
        entityManager.persist(value);
    }
}
