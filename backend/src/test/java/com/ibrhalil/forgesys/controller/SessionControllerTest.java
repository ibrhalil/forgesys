package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.security.refresh.InMemoryRefreshTokenStore;
import com.ibrhalil.forgesys.security.refresh.IssuedRefresh;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SessionController} (K-28): self-service + admin active-session views and
 * revoke. Runs on the test profile ({@link InMemoryRefreshTokenStore}); sessions are
 * seeded directly via the store. Tenant context is unset in these tests, so the
 * service resolves {@code currentTenant() = null} — sessions are seeded with the same
 * null tenant so the index keys line up.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionControllerTest extends AbstractRbacWebTest {

    private static final String REFRESH_COOKIE = "sf_refresh_token";
    private static final String WRITE = "iam:user:write";

    @Autowired
    InMemoryRefreshTokenStore refreshTokenStore;

    @Autowired
    PasswordEncoder passwordEncoder;

    /* ── self ── */

    @Test
    void mySessionsFlagsTheCookieSessionAsCurrent() throws Exception {
        User user = seedUser("s1@tenant.test", "s1");
        IssuedRefresh current = issue(user, "10.0.0.1", "Mozilla/5.0 Mac");
        issue(user, "10.0.0.2", "curl/8.0");
        UUID currentId = sessionId(current);

        mockMvc.perform(get("/api/v1/users/me/sessions")
                        .cookie(auth(user.getId(), user.getEmail()))
                        .cookie(new Cookie(REFRESH_COOKIE, current.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.current == true)].sessionId").value(currentId.toString()));
    }

    @Test
    void mySessionsWithoutRefreshCookieMarksNothingCurrent() throws Exception {
        User user = seedUser("s2@tenant.test", "s2");
        issue(user, "10.0.0.1", "UA");

        mockMvc.perform(get("/api/v1/users/me/sessions").cookie(auth(user.getId(), user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.current == true)]").isEmpty());
    }

    @Test
    void revokeMySessionEndsASingleDevice() throws Exception {
        User user = seedUser("s3@tenant.test", "s3");
        IssuedRefresh keep = issue(user, "10.0.0.1", "UA-1");
        IssuedRefresh end = issue(user, "10.0.0.2", "UA-2");

        mockMvc.perform(delete("/api/v1/users/me/sessions/{sessionId}", sessionId(end))
                        .cookie(auth(user.getId(), user.getEmail())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/sessions")
                        .cookie(auth(user.getId(), user.getEmail()))
                        .cookie(new Cookie(REFRESH_COOKIE, keep.token())))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void revokeMyUnknownSessionReturns404() throws Exception {
        User user = seedUser("s4@tenant.test", "s4");
        mockMvc.perform(delete("/api/v1/users/me/sessions/{sessionId}", UUID.randomUUID())
                        .cookie(auth(user.getId(), user.getEmail())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("session_not_found"));
    }

    @Test
    void mySessionsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/sessions"))
                .andExpect(status().isUnauthorized());
    }

    /* ── admin ── */

    @Test
    void adminListsAnotherUsersSessions() throws Exception {
        User user = seedUser("a1@tenant.test", "a1");
        issue(user, "10.0.0.1", "UA-1");
        issue(user, "10.0.0.2", "UA-2");

        mockMvc.perform(get("/api/v1/users/{id}/sessions", user.getId())
                        .cookie(auth("admin@tenant.test", WRITE)))
                .andExpect(status().isOk())
                // admin is not the owner → nothing flagged current
                .andExpect(jsonPath("$[?(@.current == true)]").isEmpty())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void adminRevokeAllEndsEverySession() throws Exception {
        User user = seedUser("a2@tenant.test", "a2");
        issue(user, "10.0.0.1", "UA-1");
        issue(user, "10.0.0.2", "UA-2");

        mockMvc.perform(delete("/api/v1/users/{id}/sessions", user.getId())
                        .cookie(auth("admin@tenant.test", WRITE)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/{id}/sessions", user.getId())
                        .cookie(auth("admin@tenant.test", WRITE)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminEndpointsRequireWritePermission() throws Exception {
        User user = seedUser("a3@tenant.test", "a3");
        issue(user, "10.0.0.1", "UA-1");

        // read-only user → 403
        mockMvc.perform(get("/api/v1/users/{id}/sessions", user.getId())
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── helpers ── */

    /** Issues a session for the user under the null tenant (matches the test's unset TenantContext). */
    private IssuedRefresh issue(User user, String ip, String userAgent) {
        return refreshTokenStore.issue(user.getId(), user.getEmail(), null, ip, userAgent);
    }

    private UUID sessionId(IssuedRefresh refresh) {
        return refreshTokenStore.activeSessionFor(refresh.token()).orElseThrow().sessionId();
    }

    private User seedUser(String email, String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
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
