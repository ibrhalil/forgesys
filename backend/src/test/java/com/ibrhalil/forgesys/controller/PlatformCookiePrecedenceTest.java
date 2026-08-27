package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.PlatformUser;
import com.ibrhalil.forgesys.entity.PlatformUserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cookie precedence in {@code JwtAuthenticationFilter} is PATH-scoped: the platform
 * cookie wins on {@code /api/v1/platform/**}, the tenant cookie everywhere else.
 * Locks the 0.2.1 regression where a browser holding BOTH cookies (the platform
 * access cookie was set on {@code Path=/} at the time) got its tenant requests
 * rejected because the platform token was validated instead of the tenant one.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformCookiePrecedenceTest extends AbstractRbacWebTest {

    @Autowired
    PasswordEncoder passwordEncoder;

    private PlatformUser platformUser;

    @BeforeEach
    void seedPlatformUser() {
        PlatformUser user = new PlatformUser();
        user.setEmail("root@platform.test");
        user.setDisplayName("Root");
        user.setUserType(PlatformUserType.HUMAN);
        user.setPasswordHash(passwordEncoder.encode("Dummy123!"));
        user.setEnabled(true);
        entityManager.persist(user);
        entityManager.flush();
        platformUser = user;
    }

    @Nested
    class TenantPath {

        /** The 0.2.1 regression: both cookies present -> the tenant cookie must win. */
        @Test
        void bothCookiesTenantCookieWins() throws Exception {
            mockMvc.perform(get("/api/v1/roles")
                            .cookie(authPlatform(platformUser.getId(), platformUser.getEmail()))
                            .cookie(auth("tenant@tenant.test", "iam:role:read")))
                    .andExpect(status().isOk());
        }

        /**
         * A platform identity alone on a tenant endpoint is authenticated but carries
         * only platform:* authorities -> 403 (with a live tenant context the filter
         * rejects the token outright -> 401 in dev/prod).
         */
        @Test
        void platformCookieOnlyIsNotATenantPrincipal() throws Exception {
            mockMvc.perform(get("/api/v1/roles")
                            .cookie(authPlatform(platformUser.getId(), platformUser.getEmail())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class PlatformPath {

        @Test
        void bothCookiesPlatformCookieWins() throws Exception {
            mockMvc.perform(get("/api/v1/platform/me")
                            .cookie(authPlatform(platformUser.getId(), platformUser.getEmail()))
                            .cookie(auth("tenant@tenant.test", "iam:role:read")))
                    .andExpect(status().isOk());
        }

        /** A tenant token on a platform endpoint authenticates but fails the scope gate. */
        @Test
        void tenantCookieOnlyFailsScopeGate() throws Exception {
            mockMvc.perform(get("/api/v1/platform/me")
                            .cookie(auth("tenant@tenant.test", "iam:role:read")))
                    .andExpect(status().isForbidden());
        }
    }
}
