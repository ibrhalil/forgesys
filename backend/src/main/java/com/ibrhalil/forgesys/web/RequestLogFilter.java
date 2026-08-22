package com.ibrhalil.forgesys.web;

import com.ibrhalil.forgesys.service.RequestLogService;
import com.ibrhalil.forgesys.web.AuditRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Captures per-request trace metadata and writes a {@link RequestLog} entry after
 * the request completes. Runs after {@link RequestMetadataFilter} (order -102) and
 * {@link TenantFilter} (order -101), but before the Spring Security chain (order -100)
 * so the tenant context and authentication are available for the log entry.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50) // After RequestMetadataFilter (-102), TenantFilter (-101), before Security (-100)
@RequiredArgsConstructor
public class RequestLogFilter extends OncePerRequestFilter {

    private final RequestLogService requestLogService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant start = Instant.now();
        String traceId = RequestContext.current()
                .map(RequestMeta::traceId)
                .orElseGet(() -> request.getHeader(RequestMetadataFilter.TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();
            int status = response.getStatus();

            // Extract user info from SecurityContext
            UUID userId = null;
            String username = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.ibrhalil.forgesys.security.CustomUserDetails user) {
                userId = user.getUserId();
                username = user.getEmail();
            }

            // Get request metadata from RequestContext
            String ipAddress = null;
            String userAgent = null;
            var meta = RequestContext.current();
            if (meta.isPresent()) {
                ipAddress = meta.get().clientIp();
                userAgent = meta.get().userAgent();
            }

            // Get masked request body from AuditRequestContext (high-risk paths only)
            String requestBody = AuditRequestContext.getAndClearRequestBody();

            requestLogService.record(traceId, request.getMethod(), request.getRequestURI(), status, durationMs,
                    userId, username, ipAddress, userAgent, requestBody);
        }
    }
}