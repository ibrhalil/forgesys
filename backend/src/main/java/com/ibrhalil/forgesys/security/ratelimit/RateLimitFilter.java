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
 * Edge rate limiting of the public auth endpoints (Faz 3), keyed by
 * {@code scope:tenant:clientIp} — complements the per-account lockout (RISK-22) by
 * closing the credential-stuffing path (one IP across many accounts; unknown emails
 * never increment any lockout counter). Runs before JWT decode; a blocked request
 * short-circuits with {@code 429 auth_rate_limited} + {@code Retry-After}.
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
        if (uri.startsWith("/api/v1/auth/verify-email")) {
            return "verify-email";
        }
        if (uri.startsWith("/api/v1/auth/forgot-password")) {
            return "forgot-password";
        }
        if (uri.startsWith("/api/v1/auth/reset-password")) {
            return "reset-password";
        }
        if (uri.startsWith("/api/v1/auth/login")) {
            return "login";
        }
        if (uri.startsWith("/api/v1/auth/refresh")) {
            return "refresh";
        }
        // K-50: platform auth runs without a tenant context (key tenant part = "public").
        if (uri.startsWith("/api/v1/platform/auth/login")) {
            return "platform-login";
        }
        if (uri.startsWith("/api/v1/platform/auth/refresh")) {
            return "platform-refresh";
        }
        return null;
    }
}
