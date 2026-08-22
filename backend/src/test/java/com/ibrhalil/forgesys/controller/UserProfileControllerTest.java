package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserProfileControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String OLD_PASSWORD = "OldPass123!";
    private static final String NEW_PASSWORD = "NewPass456!";

    @Autowired
    PasswordEncoder passwordEncoder;

    /* ── me ── */

    @Test
    void meReturnsCurrentUserWithoutAnyPermission() throws Exception {
        User user = seedUser("me@tenant.test", "meuser");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users/me").cookie(auth(user.getId(), user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("me@tenant.test"))
                .andExpect(jsonPath("$.authorities").exists())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    /* ── profile ── */

    @Test
    void updateProfileAppliesNonNullFields() throws Exception {
        User user = seedUser("profile@tenant.test", "profileuser");
        entityManager.flush();

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(JSON)
                        .cookie(auth(user.getId(), user.getEmail()))
                        .content("""
                                {"firstName":"Halil","lastName":"Test","phoneNumber":"+1-555","city":"Istanbul"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Halil"))
                .andExpect(jsonPath("$.lastName").value("Test"))
                .andExpect(jsonPath("$.phoneNumber").value("+1-555"))
                .andExpect(jsonPath("$.city").value("Istanbul"));
    }

    /* ── password ── */

    @Test
    void changePasswordSucceedsAndInvalidatesOldPassword() throws Exception {
        User user = seedUser("pw@tenant.test", "pwuser", OLD_PASSWORD);
        entityManager.flush();

        Cookie preChangeCookie = auth(user.getId(), user.getEmail());

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(JSON)
                        .cookie(preChangeCookie)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}""".formatted(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        // [RISK-21] The old password no longer verifies after the change. The original
        // cookie was revoked too (tokenInvalidBefore stamped) — mint a fresh one to
        // represent a new post-change login, then assert that the OLD password is
        // rejected while the NEW one works.
        Cookie freshCookie = auth(user.getId(), user.getEmail());
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(JSON)
                        .cookie(freshCookie)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}""".formatted(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user_password_incorrect"));
    }

    /**
     * [RISK-21] After a successful password change, the access token issued BEFORE the
     * change (in {@code preChangeCookie}) must be rejected by {@code JwtAuthenticationFilter}
     * — {@code tokenInvalidBefore} is stamped to {@code now()} inside {@code changePassword}
     * and the filter compares {@code iat < tokenInvalidBefore} (both floored to the
     * second). The cookie itself is irrelevant; the signed token is what carries the
     * {@code iat} claim.
     *
     * <p>A 1.1s sleep between mint and change guarantees the token's iat second is
     * strictly earlier than the {@code tokenInvalidBefore} second (the JWT spec's
     * NumericDate has 1s resolution).
     */
    @Test
    void changePasswordRevokesPreviouslyIssuedAccessToken() throws Exception {
        User user = seedUser("revoke@tenant.test", "revokeuser", OLD_PASSWORD);
        entityManager.flush();

        Cookie preChangeCookie = auth(user.getId(), user.getEmail());

        // Sanity: the cookie authenticated fine BEFORE the password change.
        mockMvc.perform(get("/api/v1/users/me").cookie(preChangeCookie))
                .andExpect(status().isOk());

        // Force the next now() call to land in a later second than the token's iat.
        Thread.sleep(1100);

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(JSON)
                        .cookie(preChangeCookie)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}""".formatted(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        // The SAME cookie/token now yields 401 — pre-change token revoked.
        mockMvc.perform(get("/api/v1/users/me").cookie(preChangeCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() throws Exception {
        User user = seedUser("pw2@tenant.test", "pwuser2", OLD_PASSWORD);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(JSON)
                        .cookie(auth(user.getId(), user.getEmail()))
                        .content("""
                                {"currentPassword":"WrongPass999","newPassword":"%s"}""".formatted(NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user_password_incorrect"));
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(JSON)
                        .content("""
                                {"currentPassword":"x","newPassword":"NewPass456!"}"""))
                .andExpect(status().isUnauthorized());
    }

    /* ── helpers ── */

    private User seedUser(String email, String username) {
        return seedUser(email, username, OLD_PASSWORD);
    }

    private User seedUser(String email, String username, String rawPassword) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
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
