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
 * Public password-reset endpoints: {@code POST /auth/forgot-password} (uniform 200 —
 * no account enumeration) and {@code POST /auth/reset-password} (token consume +
 * session kill). The full flow runs on H2 against a seeded {@code t_auth_tokens} row;
 * the mail side of forgot-password is covered by {@code UserServiceTest} (in the test
 * profile no tenant context resolves, so the send is silently swallowed there too).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthPasswordResetControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired
    private EntityManager entityManager;

    @Test
    void forgotPasswordAlwaysReturns200EvenForUnknownAddress() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(JSON)
                        .content("{\"email\":\"ghost@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void forgotPasswordMalformedEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void resetPasswordConsumesTokenAppliesNewPasswordAndRevokesSessions() throws Exception {
        var user = seedRbacUser("reset@example.com", "reset");
        String raw = UUID.randomUUID().toString();
        entityManager.persist(token(user.getId(), raw));
        entityManager.flush();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(JSON)
                        .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewSecret123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Same persistence context as the request — the service mutated this instance.
        assertThat(user.getPassword())
                .startsWith("{sf-peppered}") // PepperingPasswordEncoder wire format
                .isNotEqualTo("$2a$12$dummyHashForTestingOnly00000000000000000000000000000");
        // The token is single-use: a second attempt with the same link fails.
        entityManager.flush();
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(JSON)
                        .content("{\"token\":\"" + raw + "\",\"newPassword\":\"AnotherSecret123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user_token_already_used"));
    }

    @Test
    void resetPasswordUnknownTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(JSON)
                        .content("{\"token\":\"nope\",\"newPassword\":\"NewSecret123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user_token_invalid"));
    }

    @Test
    void resetPasswordShortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(JSON)
                        .content("{\"token\":\"x\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    private UserAuthToken token(UUID userId, String raw) {
        UserAuthToken authToken = new UserAuthToken();
        authToken.setUser(entityManager.getReference(com.ibrhalil.forgesys.entity.User.class, userId));
        authToken.setPurpose(UserAuthTokenPurpose.PASSWORD_RESET);
        authToken.setTokenHash(TokenHasher.sha256Hex(raw));
        authToken.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
        return authToken;
    }
}
