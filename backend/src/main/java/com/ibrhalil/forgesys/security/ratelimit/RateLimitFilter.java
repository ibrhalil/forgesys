package com.ibrhalil.forgesys.security.ratelimit;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.exception.ApiErrorFactory;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.web.RequestContext;
import com.ibrhalil.forgesys.web.RequestMeta;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Edge rate limiting on the public auth endpoints (Faz 3). Complements the per-account
 * brute-force lockout ([RISK-22]) with a per client-IP + tenant limit, closing the
 * credential-stuffing path (one IP guessing across many accounts, and the unknown-email
 * path that never increments any account's lockout counter).
 *
 * <p>Runs inside the Spring Security chain (registered before {@code JwtAuthenticationFilter})
 * so a blocked request never reaches the controller or pays the BCrypt cost. Keyed by
 * {@code scope:tenant:clientIp}; scope is derived from the request path (login / verify /
 * refresh). On block it short-circuits with {@code 429 auth_rate_limited} + a
 * {@code Retry-After} header. Disabled wholesale via {@code forgesys.security.rate-limit.enabled}.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return scopeOf(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.effectiveEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        String scope = scopeOf(request.getRequestURI());
        String tenant = TenantContext.getCurrentTenant().orElse("public");
        String clientIp = RequestContext.current().map(RequestMeta::clientIp).orElse("unknown");
        String key = scope + ":" + tenant + ":" + clientIp;

        RateLimitResult result = rateLimiter.tryConsume(key,
                properties.effectiveCapacity(),
                properties.effectiveRefillTokens(),
                properties.effectiveRefillPeriodSeconds());
        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(Math.max(1L, result.retryAfterSeconds())));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    ApiErrorFactory.of(ErrorCode.AUTH_RATE_LIMITED, request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static String scopeOf(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.startsWith("/api/v1/auth/company/verify")) {
            return "verify";
        }
        if (uri.startsWith("/api/v1/auth/login")) {
            return "login";
        }
        if (uri.startsWith("/api/v1/auth/refresh")) {
            return "refresh";
        }
        return null;
    }
}
