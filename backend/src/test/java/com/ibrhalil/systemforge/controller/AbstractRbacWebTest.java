package com.ibrhalil.systemforge.controller;

import com.ibrhalil.systemforge.security.jwt.JwtTokenProvider;
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
}
