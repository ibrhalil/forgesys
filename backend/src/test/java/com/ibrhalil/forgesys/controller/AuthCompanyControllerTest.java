package com.ibrhalil.forgesys.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-21 public signup endpoints ([RISK-31]): {@code POST /company/register} (202) and
 * {@code POST /company/suggest-subdomain} (200). These are {@code permitAll} (no auth),
 * exercised end-to-end on H2 with the real service + {@code InMemoryVerificationSender}.
 * The verify happy-path needs a real PostgreSQL (CREATE SCHEMA + Flyway) and lives in
 * the Testcontainers {@code CrossTenantIsolationIT}; token-error codes are covered by
 * {@code TenantProvisioningServiceTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthCompanyControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Test
    void registerReturns202WithContract() throws Exception {
        mockMvc.perform(post("/api/v1/auth/company/register")
                        .contentType(JSON)
                        .content("""
                                {"companyName":"Acme","subdomain":"acme","adminEmail":"admin@acme.com",
                                 "adminPassword":"Secret123!","adminFirstName":"A","adminLastName":"B"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.companyId").exists())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.subdomain").value("acme"))
                .andExpect(jsonPath("$.status").value("PROVISIONING"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void registerInvalidSubdomainReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/company/register")
                        .contentType(JSON)
                        .content("""
                                {"companyName":"Acme","subdomain":"Invalid_Upper",
                                 "adminEmail":"admin@acme.com","adminPassword":"Secret123!"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void registerMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/company/register")
                        .contentType(JSON)
                        .content("""
                                {"companyName":"Acme"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void suggestSubdomainReturns200AndFoldsTurkishChars() throws Exception {
        mockMvc.perform(post("/api/v1/auth/company/suggest-subdomain")
                        .contentType(JSON)
                        .content("""
                                {"name":"Çığ Öğün"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").isArray())
                .andExpect(jsonPath("$.suggestions[0]").value("cig-ogun"));
    }

    @Test
    void suggestMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/company/suggest-subdomain")
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }
}
