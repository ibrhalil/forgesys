package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end login slice (Epic 2.5 minimal): credentials -> RS256 access token in an
 * httpOnly cookie -> {@code /me} authenticated via the JWT cookie. Proves the whole
 * Chunk A-D chain (uniform errors, security filter chain, JWT encode/decode, RBAC
 * authority resolution).
 *
 * <p>Test profile uses H2 with ddl-auto=create-drop; all tables live in the default
 * schema (PUBLIC). With {@code TenantContext} unset, both the seed and the request
 * resolve to {@code public} (== PUBLIC), so the seeded tenant user is reachable.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthControllerLoginTest {

    private static final String EMAIL = "admin@acme.com";
    private static final String PASSWORD = "password123";
    private static final String ACCESS_COOKIE = "sf_access_token";
    private static final String REFRESH_COOKIE = "sf_refresh_token";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
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

        Filter securityFilter = (Filter) context.getBean("springSecurityFilterChain");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @Test
    void validCredentialsReturnTokenCookieAndAuthorities() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@acme.com","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.authorities[0]").value("tasks:task:read"))
                .andExpect(result -> {
                    java.util.Collection<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
                    if (setCookieFor(cookies, ACCESS_COOKIE) == null) {
                        throw new AssertionError("missing access-token cookie");
                    }
                    if (setCookieFor(cookies, REFRESH_COOKIE) == null) {
                        throw new AssertionError("missing refresh-token cookie");
                    }
                });
    }

    @Test
    void wrongPasswordReturns401WithoutLeakingReason() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@acme.com","password":"wrong"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_bad_credentials"));
    }

    /**
     * Side-fix (last-admin session): a DISABLED account with CORRECT credentials must
     * not receive a token. The check runs after the password compare so an unknown
     * email vs disabled account cannot be distinguished without valid credentials.
     */
    @Test
    void disabledAccountWithCorrectPasswordReturns401() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.getUserAccount().setEnabled(false);
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@acme.com","password":"password123"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_account_disabled"));
    }

    @Test
    void unknownUserReturnsSame401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ghost@acme.com","password":"whatever"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_bad_credentials"));
    }

    @Test
    void meWithAccessTokenCookieReturnsCurrentUser() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(ACCESS_COOKIE, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.authorities[0]").value("tasks:task:read"));
    }

    @Test
    void meWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void logoutExpiresAccessTokenCookie() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(ACCESS_COOKIE, token)))
                .andExpect(status().isNoContent())
                .andExpect(result -> {
                    java.util.Collection<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
                    String accessExpired = setCookieFor(cookies, ACCESS_COOKIE);
                    String refreshExpired = setCookieFor(cookies, REFRESH_COOKIE);
                    if (accessExpired == null || !accessExpired.contains("Max-Age=0")) {
                        throw new AssertionError("logout did not expire the access-token cookie");
                    }
                    if (refreshExpired == null || !refreshExpired.contains("Max-Age=0")) {
                        throw new AssertionError("logout did not expire the refresh-token cookie");
                    }
                });
    }

    /**
     * Lazy pepper migration (K-23): a user seeded with a legacy pepper-less BCrypt
     * hash (pre-K-23 format) logs in successfully, and the stored hash is then
     * rehashed to the peppered format on the next successful login.
     */
    @Test
    void legacyHashIsUpgradedToPepperedOnSuccessfulLogin() throws Exception {
        String legacyEmail = "legacy@acme.com";
        String legacyPassword = "legacy-pass-123";

        User legacyUser = new User();
        legacyUser.setUsername("legacy");
        legacyUser.setEmail(legacyEmail);
        legacyUser.setPassword(new BCryptPasswordEncoder(12).encode(legacyPassword));
        legacyUser.setEmailVerified(true);

        UserAccount legacyAccount = new UserAccount();
        legacyAccount.setUser(legacyUser);
        legacyAccount.setEnabled(true);
        legacyUser.setUserAccount(legacyAccount);
        userRepository.save(legacyUser);
        entityManager.flush();
        entityManager.clear();

        String storedBefore = userRepository.findByEmail(legacyEmail).orElseThrow().getPassword();
        assertThat(storedBefore).startsWith("$2a$12$");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"legacy@acme.com","password":"legacy-pass-123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(legacyEmail));

        String storedAfter = userRepository.findByEmail(legacyEmail).orElseThrow().getPassword();
        assertThat(storedAfter).startsWith("{sf-peppered}");
        assertThat(passwordEncoder.upgradeEncoding(storedAfter)).isFalse();
        assertThat(storedAfter).isNotEqualTo(storedBefore);
    }

    /**
     * [RISK-22] Brute-force lockout: after MAX_FAILED_LOGIN_ATTEMPTS (5) wrong passwords
     * the account is locked. The attempt that trips the lock still answers
     * {@code auth_bad_credentials} (wrong password, no lock-engaged leak); the lock
     * surfaces on the next attempt — even the correct password is rejected with
     * {@code auth_account_locked} (423).
     */
    @Test
    void tooManyFailedAttemptsLockTheAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"admin@acme.com","password":"wrong"}"""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("auth_bad_credentials"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@acme.com","password":"password123"}"""))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("auth_account_locked"));

        UserAccount locked = userRepository.findByEmail(EMAIL).orElseThrow().getUserAccount();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isNotNull();
    }

    /**
     * [RISK-22] Once the lock window has expired, a correct login succeeds and the
     * attempt counter is reset (fresh attempt budget).
     */
    @Test
    void loginSucceedsAfterLockWindowExpires() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        UserAccount account = user.getUserAccount();
        account.setLockedUntil(OffsetDateTime.now().minusMinutes(1));
        account.setFailedLoginAttempts(5);
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@acme.com","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        UserAccount refreshed = userRepository.findByEmail(EMAIL).orElseThrow().getUserAccount();
        assertThat(refreshed.getFailedLoginAttempts()).isZero();
        assertThat(refreshed.getLockedUntil()).isNull();
    }

    /**
     * [RISK-22] Admin unlock: an actively locked account becomes loginable
     * IMMEDIATELY after {@code DELETE /users/{id}/lock} — no waiting out the window.
     */
    @Test
    void adminUnlockAllowsLoginImmediately() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        UserAccount account = user.getUserAccount();
        account.setLockedUntil(OffsetDateTime.now().plusMinutes(10));
        account.setFailedLoginAttempts(5);
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        // Locked: even the correct password is rejected.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@acme.com","password":"password123"}"""))
                .andExpect(status().isLocked());

        String token = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID().toString(), "admin-writer@acme.com", "public",
                java.util.List.of("iam:user:write"));
        User lockedUser = userRepository.findByEmail(EMAIL).orElseThrow();
        mockMvc.perform(delete("/api/v1/users/{id}/lock", lockedUser.getId())
                        .cookie(new Cookie(ACCESS_COOKIE, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@acme.com","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    private String loginAndGetToken() throws Exception {
        java.util.Collection<String> setCookies = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@acme.com","password":"password123"}"""))
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        // Set-Cookie value: sf_access_token=<jwt>; Path=/; ...
        String setCookie = setCookieFor(setCookies, ACCESS_COOKIE);
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    /** Picks the Set-Cookie header line whose value starts with {@code <cookieName>=}. */
    private static String setCookieFor(java.util.Collection<String> setCookies, String cookieName) {
        return setCookies.stream()
                .filter(c -> c.startsWith(cookieName + "="))
                .findFirst()
                .orElse(null);
    }
}
