package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.PlatformApiKey;
import com.ibrhalil.forgesys.entity.PlatformUser;
import com.ibrhalil.forgesys.entity.PlatformUserType;
import com.ibrhalil.forgesys.persistence.repository.PlatformApiKeyRepository;
import com.ibrhalil.forgesys.security.TokenHasher;
import com.ibrhalil.forgesys.service.PlatformAuditService;
import com.ibrhalil.forgesys.service.PlatformServiceAccountService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-50 F5 service accounts: creation (raw key exactly once), list hygiene
 * (raw/hash never exposed), revoke, X-API-Key authentication (happy + every
 * failure branch), scope enforcement and the platform-auth exemption. H2 only —
 * the filter's tenant-context rejection is asserted directly (RISK-19 symmetry).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformServiceAccountControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String BASE = "/api/v1/platform/service-accounts";
    private static final String API_KEY_HEADER = "X-API-Key";

    @Autowired
    PlatformServiceAccountService platformServiceAccountService;

    @Autowired
    PlatformApiKeyRepository platformApiKeyRepository;

    /* ── auth / permission gates ── */

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post(BASE).contentType(JSON)
                        .content("{\"name\":\"Agent\",\"scopes\":[\"platform:company:read\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void createForbiddenWithoutManagePermission() throws Exception {
        mockMvc.perform(post(BASE).contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "reader@platform.test",
                                List.of("platform:company:read")))
                        .content("{\"name\":\"Agent\",\"scopes\":[\"platform:company:read\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /** RISK-18 posture: a tenant JWT never reaches the platform surface, legacy authority or not. */
    @Test
    void createForbiddenForTenantJwtEvenWithAuthority() throws Exception {
        mockMvc.perform(post(BASE).contentType(JSON)
                        .cookie(auth("svc@tenant.test", "platform:service-account:manage"))
                        .content("{\"name\":\"Agent\",\"scopes\":[\"platform:company:read\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── create ── */

    @Test
    void createReturnsRawKeyExactlyOnceAndStoresOnlyItsHash() throws Exception {
        String body = mockMvc.perform(post(BASE).contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test"))
                        .content("""
                                {"name":"CI Agent","scopes":["platform:company:read","platform:tenant:access"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawKey").exists())
                .andExpect(jsonPath("$.name").value("CI Agent"))
                .andExpect(jsonPath("$.keyPrefix").exists())
                .andReturn().getResponse().getContentAsString();

        String rawKey = JsonPath.read(body, "$.rawKey");
        String keyId = JsonPath.read(body, "$.id");
        // ~8-char unambiguous prefix + 43-char base64url secret (32 random bytes).
        assertThat(rawKey).matches("^[A-Z2-9]{8}_[A-Za-z0-9_-]{43}$");

        PlatformApiKey stored = platformApiKeyRepository.findByKeyPrefix(rawKey.substring(0, 8)).orElseThrow();
        assertThat(stored.getId()).isEqualTo(UUID.fromString(keyId));
        assertThat(stored.getKeyHash()).isEqualTo(TokenHasher.sha256Hex(rawKey));
        assertThat(stored.getScopes()).isEqualTo("platform:company:read,platform:tenant:access");
        assertThat(stored.getRevokedAt()).isNull();

        PlatformUser account = stored.getPlatformUser();
        assertThat(account.getUserType()).isEqualTo(PlatformUserType.SERVICE);
        assertThat(account.getPasswordHash()).isNull();
        assertThat(account.isEnabled()).isTrue();

        assertThatAuditContains(PlatformAuditService.ACTION_API_KEY_CREATED, stored.getId());
    }

    @Test
    void createWithFutureExpiryEchoesIt() throws Exception {
        mockMvc.perform(post(BASE).contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test"))
                        .content("""
                                {"name":"Expiring","scopes":["platform:audit:read"],"expiresAt":"2030-01-01T00:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt", org.hamcrest.Matchers.startsWith("2030-01-01T00:00")));
    }

    @Test
    void createRejectsUnknownScope() throws Exception {
        mockMvc.perform(post(BASE).contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test"))
                        .content("{\"name\":\"Bad\",\"scopes\":[\"platform:company:nope\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("business_error"));
    }

    @Test
    void createRejectsPastExpiry() throws Exception {
        mockMvc.perform(post(BASE).contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test"))
                        .content("""
                                {"name":"Past","scopes":["platform:company:read"],"expiresAt":"2000-01-01T00:00:00Z"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("business_error"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post(BASE).contentType(JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test"))
                        .content("{\"name\":\"\",\"scopes\":[\"platform:company:read\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /* ── list ── */

    @Test
    void listNeverExposesRawKeyOrHash() throws Exception {
        createKey(List.of("platform:company:read"));

        mockMvc.perform(get(BASE).cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Seeded Agent"))
                .andExpect(jsonPath("$.data[0].scopes[0]").value("platform:company:read"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].rawKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].keyHash").doesNotExist());
    }

    @Test
    void listRejectsUnknownSortProperty() throws Exception {
        mockMvc.perform(get(BASE).param("sort", "keyHash")
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /* ── revoke ── */

    @Test
    void revokeDisablesKeyAndAccountAndFutureUseIs401() throws Exception {
        String rawKey = createKey(List.of("platform:company:read"));
        PlatformApiKey key = platformApiKeyRepository.findByKeyPrefix(rawKey.substring(0, 8)).orElseThrow();

        mockMvc.perform(delete(BASE + "/" + key.getId())
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isNoContent());

        assertThat(key.getRevokedAt()).isNotNull();
        assertThat(key.getPlatformUser().isEnabled()).isFalse();
        assertThatAuditContains(PlatformAuditService.ACTION_API_KEY_REVOKED, key.getId());

        mockMvc.perform(get("/api/v1/platform/companies").header(API_KEY_HEADER, rawKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("platform_api_key_invalid"));
    }

    @Test
    void revokeUnknownReturns404() throws Exception {
        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID())
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void revokeTwiceReturns404() throws Exception {
        String rawKey = createKey(List.of("platform:company:read"));
        PlatformApiKey key = platformApiKeyRepository.findByKeyPrefix(rawKey.substring(0, 8)).orElseThrow();

        mockMvc.perform(delete(BASE + "/" + key.getId())
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(BASE + "/" + key.getId())
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isNotFound());
    }

    /* ── X-API-Key authentication ── */

    @Test
    void validKeyAuthenticatesAndTouchesLastUsed() throws Exception {
        String rawKey = createKey(List.of("platform:company:read"));

        mockMvc.perform(get("/api/v1/platform/companies").header(API_KEY_HEADER, rawKey))
                .andExpect(status().isOk());

        PlatformApiKey key = platformApiKeyRepository.findByKeyPrefix(rawKey.substring(0, 8)).orElseThrow();
        assertThat(key.getLastUsedAt()).isNotNull();
    }

    @Test
    void wrongSecretIs401() throws Exception {
        String rawKey = createKey(List.of("platform:company:read"));
        UUID keyId = platformApiKeyRepository.findByKeyPrefix(rawKey.substring(0, 8)).orElseThrow().getId();

        mockMvc.perform(get("/api/v1/platform/companies")
                        .header(API_KEY_HEADER, rawKey + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("platform_api_key_invalid"));
        assertThatAuditContains(PlatformAuditService.ACTION_API_KEY_AUTH_FAILED, keyId);
    }

    @Test
    void unknownKeyIs401() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies")
                        .header(API_KEY_HEADER, "QQQQQQQQ_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("platform_api_key_invalid"));
    }

    @Test
    void malformedKeyIs401() throws Exception {
        mockMvc.perform(get("/api/v1/platform/companies").header(API_KEY_HEADER, "no-underscore-here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("platform_api_key_invalid"));
    }

    @Test
    void expiredKeyIs401() throws Exception {
        String rawKey = createKey(List.of("platform:company:read"));
        PlatformApiKey key = platformApiKeyRepository.findByKeyPrefix(rawKey.substring(0, 8)).orElseThrow();
        key.setExpiresAt(OffsetDateTime.now().minusMinutes(1));

        mockMvc.perform(get("/api/v1/platform/companies").header(API_KEY_HEADER, rawKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("platform_api_key_invalid"));
    }

    @Test
    void disabledAccountKeyIs401() throws Exception {
        String rawKey = createKey(List.of("platform:company:read"));
        PlatformApiKey key = platformApiKeyRepository.findByKeyPrefix(rawKey.substring(0, 8)).orElseThrow();
        key.getPlatformUser().setEnabled(false);

        mockMvc.perform(get("/api/v1/platform/companies").header(API_KEY_HEADER, rawKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("platform_api_key_invalid"));
    }

    /** A key without the needed scope authenticates but is denied at the @PreAuthorize gate. */
    @Test
    void keyWithoutManageScopeCannotManageServiceAccounts() throws Exception {
        String rawKey = createKey(List.of("platform:company:read"));

        mockMvc.perform(get("/api/v1/platform/companies").header(API_KEY_HEADER, rawKey))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE).header(API_KEY_HEADER, rawKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /** The filter never runs on the platform auth surface — a garbage key must not shadow login errors. */
    @Test
    void apiKeyFilterSkippedForPlatformAuthEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/platform/auth/login").contentType(JSON)
                        .header(API_KEY_HEADER, "garbage")
                        .content("{\"email\":\"ghost@platform.test\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_bad_credentials"));
    }

    /** RISK-19 symmetry: platform identities are tenant-less — under an active tenant context the key is rejected. */
    @Test
    void validKeyRejectedUnderActiveTenantContext() throws Exception {
        String rawKey = createKey(List.of("platform:company:read"));
        TenantContext.setCurrentTenant("tenant_does_not_exist");
        try {
            mockMvc.perform(get("/api/v1/platform/companies").header(API_KEY_HEADER, rawKey))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("platform_api_key_invalid"));
        } finally {
            TenantContext.clear();
        }
    }

    /* ── helpers ── */

    /** Creates a key via the service (bypasses HTTP) — seeding for filter-focused tests. */
    private String createKey(List<String> scopes) {
        var request = new com.ibrhalil.forgesys.dto.PlatformServiceAccountCreateRequest(
                "Seeded Agent", scopes, null);
        return platformServiceAccountService.create(request, UUID.randomUUID()).rawKey();
    }

    /**
     * Membership assertion (REQUIRES_NEW audit writes commit outside the test tx —
     * never assert counts); targetId is unique per key, so cross-test rows never match.
     */
    private void assertThatAuditContains(String action, UUID targetId) {
        Long count = entityManager.createQuery(
                        "select count(a) from PlatformAuditLog a where a.action = :action and a.targetId = :targetId",
                        Long.class)
                .setParameter("action", action)
                .setParameter("targetId", targetId)
                .getSingleResult();
        assertThat(count).isGreaterThan(0);
    }
}
