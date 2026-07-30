package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end refresh flow (K-34): login → rotate via {@code /auth/refresh} → reuse of a
 * consumed token triggers session-wide revocation. Runs on the test profile
 * ({@link com.ibrhalil.forgesys.security.refresh.InMemoryRefreshTokenStore} +
 * {@link com.ibrhalil.forgesys.security.InMemoryTokenBlacklistService}); the Redis
 * counterpart's atomicity is covered by {@code RedisRefreshTokenIT}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthRefreshControllerTest extends AbstractRbacWebTest {

    private static final String EMAIL = "admin@refresh.com";
    private static final String PASSWORD = "password123";
    private static final String REFRESH_COOKIE = "sf_refresh_token";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void seedUser() {
        Permission read = new Permission();
        read.setName("tasks:task:read");
        entityManager.persist(read);

        Role role = new Role();
        role.setName("member");
        role.setPermissions(new HashSet<>(Set.of(read)));
        entityManager.persist(role);

        User user = new User();
        user.setUsername("admin");
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEmailVerified(true);
        user.setRoles(new HashSet<>(Set.of(role)));

        UserAccount account = new UserAccount();
        account.setUser(user);
        account.setEnabled(true);
        user.setUserAccount(account);

        userRepository.save(user);
    }

    @Test
    void refreshRotatesTokensAndOldRefreshBecomesReuse() throws Exception {
        String[] loginCookies = login();
        String oldAccess = loginCookies[0];
        String oldRefresh = loginCookies[1];

        // Legitimate refresh: a brand-new access + rotated refresh token come back.
        String[] rotated = refresh(oldRefresh);
        String newAccess = rotated[0];
        String newRefresh = rotated[1];
        assertThatNotEquals(oldRefresh, newRefresh);

        // The new access token authenticates.
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(COOKIE_NAME, newAccess)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        // Replaying the already-consumed refresh token is reuse → 401 + sessions revoked.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, oldRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_refresh_token_reuse"));

        // Reuse revoked ALL of the user's refresh tokens (revokeAllForUser), so even the
        // freshly-rotated token is dead. (tokenInvalidBefore is also stamped, but the
        // filter floors the compare to the second — same-second login+reuse tolerates the
        // access token; that timing-tolerance is documented [RISK-21] and tested elsewhere.)
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, newRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_refresh_token_invalid"));
    }

    @Test
    void refreshWithoutTokenIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_refresh_token_invalid"));
    }

    @Test
    void refreshWithBogusTokenIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, "never-issued")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_refresh_token_invalid"));
    }

    @Test
    void logoutBlacklistsCurrentAccessToken() throws Exception {
        String[] loginCookies = login();
        String access = loginCookies[0];
        String refresh = loginCookies[1];

        // The access token works before logout.
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(COOKIE_NAME, access)))
                .andExpect(status().isOk());

        // Per-session logout: blacklists this access token's jti (other devices untouched).
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(COOKIE_NAME, access))
                        .cookie(new Cookie(REFRESH_COOKIE, refresh)))
                .andExpect(status().isNoContent());

        // The blacklisted access token is now rejected.
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(COOKIE_NAME, access)))
                .andExpect(status().isUnauthorized());

        // The consumed refresh token is single-use now.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_refresh_token_invalid"));
    }

    @Test
    void refreshAcceptsTokenFromBodyForApiClient() throws Exception {
        String[] loginCookies = login();
        String refresh = loginCookies[1];

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    // --- helpers --------------------------------------------------------

    /** Returns {@code [accessToken, refreshToken]} extracted from the login Set-Cookie headers. */
    private String[] login() throws Exception {
        Collection<String> setCookies = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@refresh.com","password":"password123"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return new String[]{cookieValue(setCookies, COOKIE_NAME), cookieValue(setCookies, REFRESH_COOKIE)};
    }

    /** POSTs {@code /auth/refresh} with the refresh cookie and returns {@code [access, refresh]}. */
    private String[] refresh(String refreshToken) throws Exception {
        Collection<String> setCookies = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return new String[]{cookieValue(setCookies, COOKIE_NAME), cookieValue(setCookies, REFRESH_COOKIE)};
    }

    private static String cookieValue(Collection<String> setCookies, String name) {
        String header = setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst().orElseThrow();
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private static void assertThatNotEquals(String a, String b) {
        if (a == null || b == null || a.equals(b)) {
            throw new AssertionError("expected distinct tokens but got [" + a + "] and [" + b + "]");
        }
    }
}
