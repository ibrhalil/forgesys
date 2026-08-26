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
 * Captures per-request metadata (trace id, client IP, User-Agent) into
 * {@link RequestContext} + MDC for every request (K-19). Order -102 — before the
 * tenant filter (-101) and security chain (-100) — so downstream errors carry a
 * stable trace id; ThreadLocals cleared in {@code finally}.
 * rationale: docs/CODE_NOTES.md (backend/web → RequestMetadataFilter)
 */
@Component
public class RequestMetadataFilter extends OncePerRequestFilter {

    /** Header consulted for an inbound trace id. */
    public static final String TRACE_ID_HEADER = "X-Request-Id";

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String REAL_IP = "X-Real-IP";
    private static final String USER_AGENT = "User-Agent";

    // Bounded charset/length — prevents MDC / log forging via a hostile header.
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    // Matches the t_login_history.user_agent column limit.
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
            // "client, proxy1, proxy2" — the first token is the originating client.
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
