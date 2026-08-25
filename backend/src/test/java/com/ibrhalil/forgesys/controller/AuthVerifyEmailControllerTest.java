package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.UserAuthToken;
import com.ibrhalil.forgesys.entity.UserAuthTokenPurpose;
import com.ibrhalil.forgesys.security.TokenHasher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public {@code POST /api/v1/auth/verify-email} (user lifecycle, optional policy) +
 * {@code POST /api/v1/users/{id}/resend-verification} RBAC. The full consume flow runs
 * end-to-end on H2 against a seeded {@code t_auth_tokens} row; the resend happy path
 * (mail delivery needs a real tenant context) is covered by {@code UserServiceTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthVerifyEmailControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired
    private EntityManager entityManager;

    @Test
    void verifyEmailIsPublicAndConsumesToken() throws Exception {
        var user = seedRbacUser("verify@example.com", "verify");
        String raw = UUID.randomUUID().toString();
        entityManager.persist(token(user.getId(), raw));
        entityManager.flush();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(JSON)
                        .content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Same transaction/persistence context as the request — the service flipped
        // the flag on this very instance.
        assertThat(user.isEmailVerified()).isTrue();
    }

    /**
     * B1 idempotency: re-clicking an already-consumed link on a verified user
     * returns 200 (the first click did the work), not 400 already-used.
     */
    @Test
    void verifyEmailReClickedLinkOnVerifiedUserReturns200() throws Exception {
        var user = seedRbacUser("verify2@example.com", "verify2");
        String raw = UUID.randomUUID().toString();
        UserAuthToken consumed = token(user.getId(), raw);
        consumed.setUsedAt(OffsetDateTime.now());
        entityManager.persist(consumed);
        user.setEmailVerified(true);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(JSON)
                        .content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void verifyEmailUnknownTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(JSON)
                        .content("{\"token\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user_token_invalid"));
    }

    @Test
    void verifyEmailMissingTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void resendRequiresWriteAuthority() throws Exception {
        var target = seedRbacUser("target@example.com", "target");

        // Read-only caller -> 403 (method security fires before any mail logic).
        mockMvc.perform(post("/api/v1/users/" + target.getId() + "/resend-verification")
                        .cookie(auth("reader@example.com", "iam:user:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void resendUnknownUserReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/resend-verification")
                        .cookie(auth("admin@example.com", "iam:user:write")))
                .andExpect(status().isNotFound());
    }

    private UserAuthToken token(UUID userId, String raw) {
        UserAuthToken token = new UserAuthToken();
        token.setUser(entityManager.getReference(com.ibrhalil.forgesys.entity.User.class, userId));
        token.setPurpose(UserAuthTokenPurpose.EMAIL_VERIFY);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return token;
    }
}
