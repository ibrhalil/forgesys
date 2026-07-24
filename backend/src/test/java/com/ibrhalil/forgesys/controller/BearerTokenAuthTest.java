package com.ibrhalil.forgesys.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the {@code JwtAuthenticationFilter} accepts both the {@code sf_access_token}
 * cookie and the {@code Authorization: Bearer <token>} header for authentication.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BearerTokenAuthTest extends AbstractRbacWebTest {

    @Test
    void bearerTokenAuthenticatesSuccessfully() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID().toString(), "bearer@tenant.test", "public", List.of("iam:role:read"));

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * [RISK-19] A token minted for a different tenant than the request is rejected.
     * The test profile runs with {@code TenantContext} unset -> resolved as "public",
     * so a token carrying any other tenant schema is a cross-tenant replay attempt
     * (Tenant-A admin token replayed against Tenant-B) and must yield 401.
     */
    @Test
    void crossTenantBearerTokenIsRejected() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID().toString(), "attacker@tenant.test", "tenant_other", List.of("iam:role:read"));

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cookieStillWorksAlongsideBearer() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .cookie(auth("cookie@tenant.test", "iam:role:read")))
                .andExpect(status().isOk());
    }
}
