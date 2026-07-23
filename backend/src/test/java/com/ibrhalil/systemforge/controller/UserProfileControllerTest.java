package com.ibrhalil.systemforge.controller;

import com.ibrhalil.systemforge.entity.User;
import com.ibrhalil.systemforge.entity.UserAccount;
import com.ibrhalil.systemforge.entity.UserProfile;
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

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(JSON)
                        .cookie(auth(user.getId(), user.getEmail()))
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}""".formatted(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        // The old password no longer verifies after the change.
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(JSON)
                        .cookie(auth(user.getId(), user.getEmail()))
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}""".formatted(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user_password_incorrect"));
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
