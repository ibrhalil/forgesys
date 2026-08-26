package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

/**
 * Shared scaffolding for RBAC controller tests. Builds a MockMvc wired with the real
 * Spring Security filter chain and mints signed JWT cookies carrying whatever
 * authorities the test needs — this exercises {@code @PreAuthorize} enforcement
 * end-to-end without going through the login flow.
 *
 * <p>The test profile runs on H2 with {@code TenantContext} unset -> resolver returns
 * {@code public} (== H2's PUBLIC schema), so entities seeded here are reachable by the
 * controllers. Subclasses carry {@code @SpringBootTest @ActiveProfiles("test") @Transactional}.
 */
abstract class AbstractRbacWebTest {

    static final String COOKIE_NAME = "sf_access_token";

    @Autowired
    WebApplicationContext context;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @PersistenceContext
    EntityManager entityManager;

    MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        Filter securityFilter = (Filter) context.getBean("springSecurityFilterChain");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    /** A signed access-token cookie carrying the given authorities (empty = authenticated, no permissions). */
    Cookie auth(String email, String... authorities) {
        return auth(UUID.randomUUID(), email, authorities);
    }

    /** A signed access-token cookie bound to a specific user id — for {@code /users/me*} endpoints. */
    Cookie auth(UUID userId, String email, String... authorities) {
        String token = jwtTokenProvider.generateAccessToken(
                userId.toString(), email, "public", List.of(authorities));
        return new Cookie(COOKIE_NAME, token);
    }

    /**
     * A signed access-token cookie bound to a specific tenant schema — for platform /
     * cross-tenant tests where the request thread carries an active {@code TenantContext}
     * that must match the token ([RISK-19] binding).
     */
    Cookie authTenant(String tenant, String email, String... authorities) {
        String token = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID().toString(), email, tenant, List.of(authorities));
        return new Cookie(COOKIE_NAME, token);
    }

    /**
     * K-50: a signed platform-identity token cookie ({@code scope=platform}, no tenant
     * claim) — HUMAN superadmins implicitly carry the full platform catalog.
     */
    Cookie authPlatform(UUID userId, String email) {
        return authPlatform(userId, email,
                com.ibrhalil.forgesys.config.PlatformPermissionCatalog.ALL_NAMES);
    }

    /** Scoped variant (e.g. SERVICE-account-style subsets) for permission-gate tests. */
    Cookie authPlatform(UUID userId, String email, List<String> authorities) {
        String token = jwtTokenProvider.generatePlatformAccessToken(
                userId.toString(), email, List.copyOf(authorities));
        return new Cookie("sf_platform_access_token", token);
    }

    /**
     * Seeds the last-admin invariant baseline: an {@code all_permissions} role held by
     * an enabled user. Most write paths now run {@code LastAdminGuard}, which requires
     * at least one active admin-capable user to REMAIN after the mutation — tests that
     * exercise a happy-path mutation seed this first. Returns the admin user.
     */
    com.ibrhalil.forgesys.entity.User seedAdmin() {
        com.ibrhalil.forgesys.entity.Role adminRole = new com.ibrhalil.forgesys.entity.Role();
        adminRole.setName("Admin");
        adminRole.setAllPermissions(true);
        entityManager.persist(adminRole);

        com.ibrhalil.forgesys.entity.User admin = seedRbacUser("admin@tenant.test", "admin");
        admin.getRoles().add(adminRole);
        entityManager.merge(admin);
        return admin;
    }

    /** Minimal enabled user with account + profile (mirrors UserControllerTest.seedUser). */
    com.ibrhalil.forgesys.entity.User seedRbacUser(String email, String username) {
        com.ibrhalil.forgesys.entity.User user = new com.ibrhalil.forgesys.entity.User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("$2a$12$dummyHashForTestingOnly00000000000000000000000000000");
        user.setEmailVerified(false);

        com.ibrhalil.forgesys.entity.UserAccount account = new com.ibrhalil.forgesys.entity.UserAccount();
        account.setUser(user);
        user.setUserAccount(account);

        com.ibrhalil.forgesys.entity.UserProfile profile = new com.ibrhalil.forgesys.entity.UserProfile();
        profile.setUser(user);
        profile.setFirstName("Test");
        profile.setLastName("User");
        user.setUserProfile(profile);

        entityManager.persist(user);
        return user;
    }
}
