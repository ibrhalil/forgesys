package com.ibrhalil.systemforge.controller;

import com.ibrhalil.systemforge.entity.Permission;
import com.ibrhalil.systemforge.entity.Role;
import com.ibrhalil.systemforge.entity.User;
import com.ibrhalil.systemforge.entity.UserAccount;
import com.ibrhalil.systemforge.persistence.repository.UserRepository;
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

import java.util.HashSet;
import java.util.Set;

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
    private static final String COOKIE_NAME = "sf_access_token";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
                    String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                    if (cookie == null || !cookie.startsWith(COOKIE_NAME + "=")) {
                        throw new AssertionError("missing access-token cookie");
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

        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(COOKIE_NAME, token)))
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

    private String loginAndGetToken() throws Exception {
        String setCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@acme.com","password":"password123"}"""))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        // Set-Cookie value: sf_access_token=<jwt>; Path=/; ...
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }
}
