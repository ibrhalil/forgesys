package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.config.PlatformPermissionCatalog;
import com.ibrhalil.forgesys.entity.PlatformUser;
import com.ibrhalil.forgesys.entity.PlatformUserType;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.security.refresh.RefreshTokenStore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-50 platform auth surface: login (+lockout), refresh rotation/reuse, logout
 * blacklisting, cookie attributes and the platform/tenant scope gates.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformAuthControllerTest extends AbstractRbacWebTest {

    private static final String PLATFORM_COOKIE = "sf_platform_access_token";
    private static final String PASSWORD = "super-secret-1";

    @Autowired
    PlatformUserRepository platformUserRepository;

    @Autowired
    RefreshTokenStore refreshTokenStore;

    @Autowired
    PasswordEncoder passwordEncoder;

    private PlatformUser seedPlatformHuman() {
        PlatformUser user = new PlatformUser();
        user.setEmail("root@platform.test");
        user.setDisplayName("Root");
        user.setUserType(PlatformUserType.HUMAN);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    @Nested
    class Login {

        @Test
        void okSetsPlatformCookiesAndFullCatalog() throws Exception {
            PlatformUser user = seedPlatformHuman();
            var result = mockMvc.perform(post("/api/v1/platform/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"root@platform.test\",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                    .andExpect(jsonPath("$.displayName").value("Root"))
                    .andExpect(jsonPath("$.authorities.length()").value(PlatformPermissionCatalog.ALL_NAMES.size()))
                    .andReturn();
            String setCookies = String.join("\n", result.getResponse().getHeaders("Set-Cookie"));
            org.hamcrest.MatcherAssert.assertThat(setCookies,
                    org.hamcrest.Matchers.containsString(PLATFORM_COOKIE + "="));
            org.hamcrest.MatcherAssert.assertThat(setCookies,
                    org.hamcrest.Matchers.containsString("sf_platform_refresh_token="));
            org.hamcrest.MatcherAssert.assertThat(setCookies,
                    org.hamcrest.Matchers.containsString("Path=/api/v1/platform"));
            // The ACCESS cookie must be path-scoped too — a Path=/ platform access cookie
            // leaks into tenant requests (0.2.1 regression, see PlatformCookiePrecedenceTest).
            String accessCookie = result.getResponse().getHeaders("Set-Cookie").stream()
                    .filter(h -> h.startsWith(PLATFORM_COOKIE + "="))
                    .findFirst().orElse("");
            org.hamcrest.MatcherAssert.assertThat(accessCookie,
                    org.hamcrest.Matchers.containsString("Path=/api/v1/platform"));
        }

        @Test
        void wrongPasswordIsUniform401() throws Exception {
            seedPlatformHuman();
            mockMvc.perform(post("/api/v1/platform/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"root@platform.test\",\"password\":\"nope\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("auth_bad_credentials"));
        }

        @Test
        void unknownEmailPaysBcryptCostAndFails() throws Exception {
            mockMvc.perform(post("/api/v1/platform/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"ghost@platform.test\",\"password\":\"nope\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("auth_bad_credentials"));
        }

        @Test
        void serviceIdentityCannotPasswordLogin() throws Exception {
            PlatformUser service = new PlatformUser();
            service.setEmail("agent@platform.test");
            service.setDisplayName("Agent");
            service.setUserType(PlatformUserType.SERVICE);
            service.setEnabled(true);
            entityManager.persist(service);
            entityManager.flush();

            mockMvc.perform(post("/api/v1/platform/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"agent@platform.test\",\"password\":\"nope\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("auth_bad_credentials"));
        }

        @Test
        void fiveFailuresLockTheAccount() throws Exception {
            seedPlatformHuman();
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/v1/platform/auth/login")
                                .contentType("application/json")
                                .content("{\"email\":\"root@platform.test\",\"password\":\"nope\"}"))
                        .andExpect(status().isUnauthorized());
            }
            mockMvc.perform(post("/api/v1/platform/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"root@platform.test\",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("auth_account_locked"));
        }
    }

    @Nested
    class Refresh {

        @Test
        void rotatesAndRejectsReuse() throws Exception {
            PlatformUser user = seedPlatformHuman();
            String raw = refreshTokenStore.issue(user.getId(), user.getEmail(),
                    "platform", null, null).token();

            mockMvc.perform(post("/api/v1/platform/auth/refresh")
                            .contentType("application/json")
                            .content("{\"refreshToken\":\"" + raw + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(user.getId().toString()));

            mockMvc.perform(post("/api/v1/platform/auth/refresh")
                            .contentType("application/json")
                            .content("{\"refreshToken\":\"" + raw + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("auth_refresh_token_reuse"));
        }

        @Test
        void tenantMarkerMismatchRejected() throws Exception {
            PlatformUser user = seedPlatformHuman();
            String raw = refreshTokenStore.issue(user.getId(), user.getEmail(),
                    "tenant_somewhere", null, null).token();

            mockMvc.perform(post("/api/v1/platform/auth/refresh")
                            .contentType("application/json")
                            .content("{\"refreshToken\":\"" + raw + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("auth_refresh_token_invalid"));
        }
    }

    @Nested
    class ScopeGates {

        @Test
        void meWithPlatformTokenOk() throws Exception {
            PlatformUser user = seedPlatformHuman();
            mockMvc.perform(get("/api/v1/platform/me").cookie(authPlatform(user.getId(), user.getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("root@platform.test"))
                    .andExpect(jsonPath("$.userType").value("HUMAN"));
        }

        @Test
        void meWithTenantTokenDenied() throws Exception {
            // A tenant token (no scope claim) must not reach platform endpoints.
            mockMvc.perform(get("/api/v1/platform/me").cookie(auth("tenant-user@t.test", "iam:user:read")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedMeIs401() throws Exception {
            mockMvc.perform(get("/api/v1/platform/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void platformTokenOnTenantEndpointIsAuthenticatedButDenied() throws Exception {
            // Platform tokens authenticate (scope branch) but carry no iam:* authorities.
            mockMvc.perform(get("/api/v1/users").cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Logout {

        @Test
        void logoutBlacklistsTheJti() throws Exception {
            PlatformUser user = seedPlatformHuman();
            var login = mockMvc.perform(post("/api/v1/platform/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"root@platform.test\",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            String token = new tools.jackson.databind.ObjectMapper()
                    .readTree(login.getResponse().getContentAsString())
                    .get("accessToken").asText();
            jakarta.servlet.http.Cookie access =
                    new jakarta.servlet.http.Cookie(PLATFORM_COOKIE, token);

            mockMvc.perform(post("/api/v1/platform/auth/logout").cookie(access))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/platform/me").cookie(access))
                    .andExpect(status().isUnauthorized());
        }
    }
}
