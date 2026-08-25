package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
        mockMvc.perform(get("/api/v1/platform/companies").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void updateStatusForbiddenWithoutWritePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/platform/companies/" + UUID.randomUUID() + "/status")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "platform:company:read"))
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── list & get ── */

    @Test
    void listReturnsCompanies() throws Exception {
        Company company = seedCompany("testcompany", CompanyStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/platform/companies")
                        .cookie(auth("platform_reader@tenant.test", "platform:company:read")))
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
                        .cookie(auth("platform_reader@tenant.test", "platform:company:read"))
                        .content("""
                                {"filters":[{"field":"status","operator":"EQ","values":["SUSPENDED"]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].name").value("suspendedco"));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies/" + UUID.randomUUID())
                        .cookie(auth("platform_reader@tenant.test", "platform:company:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void getReturnsCompany() throws Exception {
        Company company = seedCompany("singlecompany", CompanyStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/platform/companies/" + company.getId())
                        .cookie(auth("platform_reader@tenant.test", "platform:company:read")))
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
                        .cookie(auth("platform_writer@tenant.test", "platform:company:write"))
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
                        .cookie(auth("platform_writer@tenant.test", "platform:company:write"))
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("business_error"));
    }

    @Test
    void updateStatusWithInvalidBodyReturns400() throws Exception {
        Company company = seedCompany("invalidtest", CompanyStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/platform/companies/" + company.getId() + "/status")
                        .contentType(JSON)
                        .cookie(auth("platform_writer@tenant.test", "platform:company:write"))
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void invalidEnumStatusReturns400Not500() throws Exception {
        Company company = seedCompany("badenum", CompanyStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/platform/companies/" + company.getId() + "/status")
                        .contentType(JSON)
                        .cookie(auth("platform_writer@tenant.test", "platform:company:write"))
                        .content("{\"status\":\"NOT_A_REAL_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /**
     * Reproduces the real cross-tenant scenario: the request thread already has an active
     * tenant context (set by TenantFilter in dev). The platform admin's token is bound to
     * that same tenant ([RISK-19]), and PlatformCompanyService must clear it to reach the
     * public schema; if it didn't, the query would run against the tenant schema (which
     * has no t_companies) and return nothing / fail.
     */
    @Test
    void listReturnsCompaniesEvenWithActiveTenantContext() throws Exception {
        Company company = seedCompany("ctxtest", CompanyStatus.ACTIVE);
        String tenant = "tenant_does_not_exist";

        TenantContext.setCurrentTenant(tenant);
        try {
            mockMvc.perform(get("/api/v1/platform/companies")
                            .cookie(authTenant(tenant, "platform_reader@tenant.test", "platform:company:read")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.id == '" + company.getId() + "')].name").value("ctxtest"));
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
