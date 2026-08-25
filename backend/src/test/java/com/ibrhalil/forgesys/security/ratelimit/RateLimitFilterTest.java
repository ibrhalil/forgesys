package com.ibrhalil.forgesys.security.ratelimit;

import com.ibrhalil.forgesys.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link RateLimitFilter} with a stub {@link RateLimiter} (Faz 3): a blocked
 * request short-circuits with {@code 429 auth_rate_limited} + a {@code Retry-After}
 * header, while an allowed request proceeds down the chain. The limiter itself and the
 * path-scope gating are covered by {@link InMemoryRateLimiterTest} / the filter logic.
 */
class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void blockedRequestReturns429WithRetryAfter() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                (key, cap, rate, period) -> new RateLimitResult(false, 30L),
                new RateLimitProperties(true, 1, 1, 60),
                objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        assertThat(response.getContentAsString()).contains(ErrorCode.AUTH_RATE_LIMITED.code());
        assertThat(chain.getRequest()).isNull(); // never proceeded
    }

    @Test
    void allowedRequestProceedsDownTheChain() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                (key, cap, rate, period) -> new RateLimitResult(true, 0L),
                new RateLimitProperties(true, 1, 1, 60),
                objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // proceeded to the chain
    }

    @Test
    void disabledFilterAlwaysProceeds() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                (key, cap, rate, period) -> new RateLimitResult(false, 30L),
                new RateLimitProperties(false, 1, 1, 60), // disabled
                objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void nonAuthPathIsSkipped() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                (key, cap, rate, period) -> new RateLimitResult(false, 30L),
                new RateLimitProperties(true, 1, 1, 60),
                objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    /**
     * K-48 user-lifecycle endpoints join the rate-limited scopes — token-consuming
     * public endpoints must be covered by the IP+tenant bucket against link guessing.
     */
    @Test
    void userLifecycleEndpointsAreRateLimited() throws Exception {
        for (String uri : new String[]{
                "/api/v1/auth/verify-email", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password"}) {
            RateLimitFilter filter = new RateLimitFilter(
                    (key, cap, rate, period) -> new RateLimitResult(false, 30L),
                    new RateLimitProperties(true, 1, 1, 60),
                    objectMapper);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).as("blocked: %s", uri).isEqualTo(429);
            assertThat(chain.getRequest()).as("short-circuited: %s", uri).isNull();
        }
    }
}
