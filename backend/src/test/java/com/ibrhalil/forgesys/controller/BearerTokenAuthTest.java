package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the {@code JwtAuthenticationFilter} accepts both the {@code sf_access_token}
 * cookie and the {@code Authorization: Bearer <token>} header for authentication, and
 * rejects tokens under the [RISK-21] revocation rules (tokenInvalidBefore).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BearerTokenAuthTest extends AbstractRbacWebTest {

    @Autowired
    PasswordEncoder passwordEncoder;

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

    /**
     * [RISK-21] A token whose {@code iat} predates {@code tokenInvalidBefore} (set by
     * password change/reset, logout) is rejected by the filter even though its
     * signature/expiry/tenant binding are all valid. Simulates the post-changePassword
     * / post-logout window where a leaked token must stop authenticating.
     *
     * <p>A 1.1s sleep between mint and stamping {@code tokenInvalidBefore} guarantees
     * the iat second is strictly earlier than the revoke second (the JWT NumericDate
     * has 1s resolution; without the gap, same-second mint+revoke floors to equal and
     * the token stays valid by design — see {@code JwtAuthenticationFilter}).
     */
    @Test
    void tokenInvalidBeforeRevokesPreviouslyIssuedToken() throws Exception {
        User user = seedAccountUser("revoke@tenant.test", "revoker");
        entityManager.flush();

        // Mint token at T1 (no tokenInvalidBefore set yet).
        String token = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), "public", List.of("iam:role:read"));

        // Force the next now() into a later second than the token's iat second.
        Thread.sleep(1100);

        // Stamp tokenInvalidBefore = now (T2 > T1) — what changePassword/logout does.
        user.getUserAccount().setTokenInvalidBefore(OffsetDateTime.now());
        entityManager.flush();

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    /**
     * [RISK-21] Negative control: a {@code tokenInvalidBefore} in the past does NOT
     * revoke a freshly minted token ({@code iat > tokenInvalidBefore}). This is the
     * post-change login path — a user whose tokens were invalidated by a password
     * change can immediately log back in and the new token authenticates.
     */
    @Test
    void tokenInvalidBeforeDoesNotAffectNewerToken() throws Exception {
        User user = seedAccountUser("stale@tenant.test", "staleuser");
        user.getUserAccount().setTokenInvalidBefore(OffsetDateTime.now().minusMinutes(5));
        entityManager.flush();

        String token = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), "public", List.of("iam:role:read"));

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private User seedAccountUser(String email, String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Dummy123!"));
        user.setEmailVerified(false);

        UserAccount account = new UserAccount();
        account.setUser(user);
        user.setUserAccount(account);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        user.setUserProfile(profile);

        entityManager.persist(user);
        return user;
    }
}
