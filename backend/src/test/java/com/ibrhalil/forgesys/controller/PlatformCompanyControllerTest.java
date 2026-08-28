package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformCompanyControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    /* ── auth / permission gates ── */

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies")
                        .cookie(authPlatform(UUID.randomUUID(), "reader@platform.test", List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void updateStatusForbiddenWithoutWritePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/platform/companies/" + UUID.randomUUID() + "/status")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "reader@platform.test",
                                List.of("platform:company:read")))
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /**
     * RISK-18 closure (K-50 F3): a TENANT JWT — even one still carrying the legacy
     * {@code platform:company:read} authority — no longer reaches the platform
     * surface; the endpoints accept {@code scope=platform} tokens only.
     */
    @Test
    void tenantJwtWithLegacyPlatformAuthorityIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies")
                        .cookie(auth("platform_reader@tenant.test", "platform:company:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── list & get ── */

    @Test
    void listReturnsCompanies() throws Exception {
        Company company = seedCompany("testcompany", CompanyStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/platform/companies")
                        .cookie(authPlatform(UUID.randomUUID(), "platform_reader@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[?(@.id == '" + company.getId() + "')].name").value("testcompany"))
                .andExpect(jsonPath("$.data[?(@.id == '" + company.getId() + "')].status").value("ACTIVE"));
    }

    /** K-49: the platform list is engine-wired — status filter + subdomain q targeting. */
    @Test
    void searchFiltersByStatus() throws Exception {
        seedCompany("activeco", CompanyStatus.ACTIVE);
        seedCompany("suspendedco", CompanyStatus.SUSPENDED);

        mockMvc.perform(post("/api/v1/platform/companies/search")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "platform_reader@platform.test"))
                        .content("""
                                {"filters":[{"field":"status","operator":"EQ","values":["SUSPENDED"]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].name").value("suspendedco"));
    }

    /** K-55: the platform list reads the engine through the GET {@code ?sq=} blob. */
    @Test
    void listReadsSearchQueryParam() throws Exception {
        seedCompany("sq_activeco", CompanyStatus.ACTIVE);
        seedCompany("sq_suspendedco", CompanyStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/platform/companies")
                        .param("sq", sq("""
                                {"v":1,"page":0,"size":10,"sorts":[{"field":"name","direction":"asc"}],
                                 "filters":[{"field":"status","operator":"EQ","values":["SUSPENDED"]}]}"""))
                        .cookie(authPlatform(UUID.randomUUID(), "platform_reader@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].name").value("sq_suspendedco"));
    }

    /** URL-safe unpadded base64 of the UTF-8 JSON — the wire form the SPA codec produces. */
    private static String sq(String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies/" + UUID.randomUUID())
                        .cookie(authPlatform(UUID.randomUUID(), "platform_reader@platform.test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void getReturnsCompany() throws Exception {
        Company company = seedCompany("singlecompany", CompanyStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/platform/companies/" + company.getId())
                        .cookie(authPlatform(UUID.randomUUID(), "platform_reader@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("singlecompany"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    /* ── update status ── */

    @Test
    void updateStatusChangesCompanyStatus() throws Exception {
        Company company = seedCompany("statustest", CompanyStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/platform/companies/" + company.getId() + "/status")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "platform_writer@platform.test"))
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    /**
     * [RISK-32] An illegal status transition (TERMINATED -> ACTIVE) is rejected with
     * 400 business_error instead of silently flipping the company into a broken state.
     */
    @Test
    void illegalStatusTransitionReturns400() throws Exception {
        Company company = seedCompany("terminated", CompanyStatus.TERMINATED);

        mockMvc.perform(patch("/api/v1/platform/companies/" + company.getId() + "/status")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "platform_writer@platform.test"))
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("business_error"));
    }

    @Test
    void updateStatusWithInvalidBodyReturns400() throws Exception {
        Company company = seedCompany("invalidtest", CompanyStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/platform/companies/" + company.getId() + "/status")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "platform_writer@platform.test"))
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void invalidEnumStatusReturns400Not500() throws Exception {
        Company company = seedCompany("badenum", CompanyStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/platform/companies/" + company.getId() + "/status")
                        .contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "platform_writer@platform.test"))
                        .content("{\"status\":\"NOT_A_REAL_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /**
     * K-50: platform tokens are valid ONLY where no tenant context was resolved — a
     * platform cookie presented under an active tenant context is rejected with 401
     * (RISK-19 symmetry from the tenant side). In production {@code /api/v1/platform/**}
     * is exempt from {@code TenantFilter}, so the context stays empty on platform paths.
     */
    @Test
    void platformTokenRejectedWhenTenantContextIsActive() throws Exception {
        TenantContext.setCurrentTenant("tenant_does_not_exist");
        try {
            mockMvc.perform(get("/api/v1/platform/companies")
                            .cookie(authPlatform(UUID.randomUUID(), "platform_reader@platform.test")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
        } finally {
            TenantContext.clear();
        }
    }

    /* ── helpers ── */

    private Company seedCompany(String name, CompanyStatus status) {
        Company company = new Company();
        company.setName(name);
        company.setSubdomain(name + "sub");
        company.setSchemaName("tenant_" + name);
        company.setStatus(status);
        entityManager.persist(company);
        entityManager.flush();
        return company;
    }
}
