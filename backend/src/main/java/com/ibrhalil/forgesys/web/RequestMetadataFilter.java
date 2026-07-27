package com.ibrhalil.forgesys.web;

import com.ibrhalil.forgesys.exception.ApiErrorFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Captures per-request metadata (trace id, client IP, User-Agent) for every
 * request and exposes it via {@link RequestContext} (for services) and the SLF4J
 * MDC (for log correlation and {@link ApiErrorFactory}). Registered to run before
 * the tenant filter (order -102) and the Spring Security chain (-100), so error
 * responses produced downstream carry a stable trace id.
 *
 * <p>The trace id is taken from the {@code X-Request-Id} header when present and
 * well-formed (alphanumeric, dots, dashes, underscores; max 128 chars &mdash;
 * prevents MDC / log forging); otherwise a fresh UUID is generated. The client
 * IP prefers {@code X-Forwarded-For} (first hop) then {@code X-Real-IP}, falling
 * back to {@link HttpServletRequest#getRemoteAddr()}; the platform runs behind a
 * trusted reverse proxy in prod (K-33 topology), so forwarded headers are
 * trusted.
 *
 * <p>Both {@link RequestContext} and the MDC are cleared in {@code finally} so the
 * values never leak across reused request threads. There is no
 * {@code shouldNotFilter} override: request metadata is useful for every path,
 * including actuator and public auth endpoints.
 */
@Component
public class RequestMetadataFilter extends OncePerRequestFilter {

    /** Header consulted for an inbound trace id. */
    public static final String TRACE_ID_HEADER = "X-Request-Id";

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String REAL_IP = "X-Real-IP";
    private static final String USER_AGENT = "User-Agent";

    /** Accept only safe, bounded trace ids to avoid MDC / log forging. */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    /** Matches {@code t_login_history.user_agent} / {@code t_audit_logs}-adjacent limits. */
    private static final int USER_AGENT_MAX = 500;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        RequestMeta meta = new RequestMeta(resolveTraceId(request), resolveClientIp(request), resolveUserAgent(request));
        try {
            RequestContext.set(meta);
            MDC.put(ApiErrorFactory.TRACE_ID_KEY, meta.traceId());
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
            RequestContext.clear();
        }
    }

    String resolveTraceId(HttpServletRequest request) {
        String header = request.getHeader(TRACE_ID_HEADER);
        if (header != null && TRACE_ID_PATTERN.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            // "client, proxy1, proxy2" &mdash; the first token is the originating client.
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader(REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader(USER_AGENT);
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > USER_AGENT_MAX ? userAgent.substring(0, USER_AGENT_MAX) : userAgent;
    }
}
