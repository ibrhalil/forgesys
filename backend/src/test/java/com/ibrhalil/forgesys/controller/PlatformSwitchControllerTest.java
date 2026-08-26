package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.PlatformSwitchExchangeRequest;
import com.ibrhalil.forgesys.dto.PlatformSwitchStartResponse;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.PlatformApiKey;
import com.ibrhalil.forgesys.entity.PlatformUser;
import com.ibrhalil.forgesys.entity.PlatformUserType;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.persistence.repository.AuditLogRepository;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlatformApiKeyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.security.TokenHasher;
import com.ibrhalil.forgesys.security.apikey.PlatformApiKeyAuthenticationFilter;
import com.ibrhalil.forgesys.security.platformswitch.InMemoryPlatformSwitchStore;
import com.ibrhalil.forgesys.service.PlatformSwitchService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-50 F6 switch flow on H2 (single-schema: TenantContext unset → "public" — the
 * happy-path company carries {@code schemaName="public"} so the RISK-19 symmetry
 * check matches; the real two-tenant schema flow is covered by the gated
 * {@code PlatformSwitchImpersonationIT}). Audit assertions use membership —
 * REQUIRES_NEW audit writes commit outside the test rollback.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformSwitchControllerTest extends AbstractRbacWebTest {

    private static final String PLATFORM_EMAIL = "root@platform.test";
    private static final String PLATFORM_DISPLAY = "Root Admin";
    private static final String START_PATH = "/api/v1/platform/companies/{id}/switch";
    private static final String EXCHANGE_PATH = "/api/v1/auth/platform-switch";

    @Autowired private CompanyRepository companyRepository;
    @Autowired private PlatformUserRepository platformUserRepository;
    @Autowired private PlatformApiKeyRepository platformApiKeyRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private InMemoryPlatformSwitchStore switchStore;
    @Autowired private ObjectMapper objectMapper;

    private UUID platformUserId;

    @BeforeEach
    void seedPlatformIdentity() {
        PlatformUser platformUser = new PlatformUser();
        platformUser.setEmail(UUID.randomUUID() + "-" + PLATFORM_EMAIL);
        platformUser.setDisplayName(PLATFORM_DISPLAY);
        platformUser.setUserType(PlatformUserType.HUMAN);
        platformUser.setEnabled(true);
        platformUserRepository.save(platformUser);
        platformUserId = platformUser.getId();
    }

    @Test
    void startReturnsCodeAndTargetUrlAndGuardsSingleActive() throws Exception {
        seedAdmin();
        Company company = activeCompany("public");

        MvcResult result = mockMvc.perform(post(START_PATH, company.getId())
                        .cookie(authPlatform(platformUserId, PLATFORM_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"support investigation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.switchCode").isNotEmpty())
                .andExpect(jsonPath("$.targetUrl").value("http://localhost:3000"))
                .andReturn();
        String code = switchCode(result);
        assertThat(code).isNotBlank();

        mockMvc.perform(post(START_PATH, company.getId())
                        .cookie(authPlatform(platformUserId, PLATFORM_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"second attempt\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("platform_switch_already_active"));
    }

    @Test
    void startRejectsBlankReason() throws Exception {
        seedAdmin();
        Company company = activeCompany("public");
        mockMvc.perform(post(START_PATH, company.getId())
                        .cookie(authPlatform(platformUserId, PLATFORM_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void startRejectsTenantToken() throws Exception {
        seedAdmin();
        Company company = activeCompany("public");
        mockMvc.perform(post(START_PATH, company.getId())
                        .cookie(auth("admin@tenant.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"support\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void startRejectsPlatformTokenWithoutSwitchAuthority() throws Exception {
        seedAdmin();
        Company company = activeCompany("public");
        mockMvc.perform(post(START_PATH, company.getId())
                        .cookie(authPlatform(platformUserId, PLATFORM_EMAIL,
                                java.util.List.of("platform:company:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"support\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void startRejectsNonActiveCompany() throws Exception {
        seedAdmin();
        Company company = activeCompany("public");
        company.setStatus(CompanyStatus.SUSPENDED);
        mockMvc.perform(post(START_PATH, company.getId())
                        .cookie(authPlatform(platformUserId, PLATFORM_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"support\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("company_not_active"));
    }

    @Test
    void startRejectsTenantWithoutAdminCapableUser() throws Exception {
        Company company = activeCompany("public");
        mockMvc.perform(post(START_PATH, company.getId())
                        .cookie(authPlatform(platformUserId, PLATFORM_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"support\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("platform_no_admin_in_tenant"));
    }

    @Test
    void exchangeSetsImpersonationCookieAndBannerData() throws Exception {
        User admin = seedAdmin();
        Company company = activeCompany("public");
        Cookie imp = exchange(start(company.getId()));

        mockMvc.perform(get("/api/v1/users/me").cookie(imp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId().toString()))
                .andExpect(jsonPath("$.impersonation.actorId").value(platformUserId.toString()))
                .andExpect(jsonPath("$.impersonation.actorEmail").value(PLATFORM_DISPLAY));

        assertThat(auditLogRepository.findAll()).anyMatch(entry ->
                PlatformSwitchService.ACTION_IMPERSONATION_STARTED.equals(entry.getAction())
                        && platformUserId.equals(entry.getActorId()));
    }

    @Test
    void exchangeRejectsReplay() throws Exception {
        seedAdmin();
        Company company = activeCompany("public");
        String code = start(company.getId());
        exchange(code);
        mockMvc.perform(post(EXCHANGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlatformSwitchExchangeRequest(code))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("platform_switch_code_invalid"));
    }

    @Test
    void exchangeRejectsExpiredCode() throws Exception {
        seedAdmin();
        Company company = activeCompany("public");
        String code = start(company.getId());
        switchStore.expireAllCodes();
        mockMvc.perform(post(EXCHANGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlatformSwitchExchangeRequest(code))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("platform_switch_code_invalid"));
    }

    @Test
    void exchangeRejectsSchemaMismatchAndBurnsTheCode() throws Exception {
        seedAdmin();
        Company otherTenant = activeCompany("tenant_other");
        String code = start(otherTenant.getId());

        mockMvc.perform(post(EXCHANGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlatformSwitchExchangeRequest(code))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));

        mockMvc.perform(post(EXCHANGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlatformSwitchExchangeRequest(code))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("platform_switch_code_invalid"));
    }

    @Test
    void exchangeIsReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(post(EXCHANGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"garbage\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("platform_switch_code_invalid"));
    }

    @Test
    void logoutEndsImpersonationAndClearsGuard() throws Exception {
        seedAdmin();
        Company company = activeCompany("public");
        Cookie imp = exchange(start(company.getId()));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(imp))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").cookie(imp))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(START_PATH, company.getId())
                        .cookie(authPlatform(platformUserId, PLATFORM_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"next session\"}"))
                .andExpect(status().isOk());
    }

    /** K-50 F5×F6: an API key WITHOUT platform:tenant:access cannot start a switch. */
    @Test
    void apiKeyWithoutSwitchScopeIsForbidden() throws Exception {
        seedAdmin();
        Company company = activeCompany("public");
        mockMvc.perform(post(START_PATH, company.getId())
                        .header(PlatformApiKeyAuthenticationFilter.API_KEY_HEADER,
                                seedApiKey("platform:company:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"agent probe\"}"))
                .andExpect(status().isForbidden());
    }

    /** K-50 F5×F6: an API key WITH platform:tenant:access starts a switch (SERVICE actor path). */
    @Test
    void apiKeyWithSwitchScopeStartsSwitch() throws Exception {
        seedAdmin();
        Company company = activeCompany("public");
        mockMvc.perform(post(START_PATH, company.getId())
                        .header(PlatformApiKeyAuthenticationFilter.API_KEY_HEADER,
                                seedApiKey("platform:tenant:access"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"agent investigation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.switchCode").isNotEmpty());
    }

    // --- helpers ---------------------------------------------------------

    /** Seeds a SERVICE account + usable API key; returns the raw {@code <prefix>_<secret>} value. */
    private String seedApiKey(String scopes) {
        PlatformUser serviceAccount = new PlatformUser();
        serviceAccount.setEmail("svc-" + UUID.randomUUID().toString().substring(0, 8) + "@service.internal");
        serviceAccount.setDisplayName("Switch Probe");
        serviceAccount.setUserType(PlatformUserType.SERVICE);
        serviceAccount.setEnabled(true);
        platformUserRepository.save(serviceAccount);

        String prefix = "swprobe" + UUID.randomUUID().toString().substring(0, 8);
        String raw = prefix + "_" + UUID.randomUUID();
        PlatformApiKey key = new PlatformApiKey();
        key.setPlatformUser(serviceAccount);
        key.setName("switch-probe");
        key.setKeyPrefix(prefix);
        key.setKeyHash(TokenHasher.sha256Hex(raw));
        key.setScopes(scopes);
        platformApiKeyRepository.save(key);
        return raw;
    }

    private Company activeCompany(String schemaName) {
        String sentinel = UUID.randomUUID().toString().substring(0, 8);
        Company company = new Company();
        company.setName("Switch Co " + sentinel);
        company.setSubdomain("switch-" + sentinel);
        company.setSchemaName(schemaName);
        company.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(company);
    }

    private String start(UUID companyId) throws Exception {
        MvcResult result = mockMvc.perform(post(START_PATH, companyId)
                        .cookie(authPlatform(platformUserId, PLATFORM_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"support investigation\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return switchCode(result);
    }

    private Cookie exchange(String code) throws Exception {
        MvcResult result = mockMvc.perform(post(EXCHANGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlatformSwitchExchangeRequest(code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        return cookie;
    }

    private String switchCode(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                PlatformSwitchStartResponse.class).switchCode();
    }
}
