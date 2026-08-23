package com.ibrhalil.forgesys.web;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.RequestLog;
import com.ibrhalil.forgesys.service.RequestLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Captures per-request trace metadata and writes a {@link RequestLog} entry after
 * the request completes. Registered INSIDE the security chain (order -95, see
 * {@code SecurityConfig#requestLogFilterRegistration}) so that in this filter's
 * {@code finally} — which unwinds before the security/tenant/metadata filters
 * clear their ThreadLocals — the tenant schema, authentication and request
 * metadata are still live when the row is written.
 *
 * <p>Skips the write when no tenant was resolved (actuator, tenant signup,
 * unknown host): {@code t_request_logs} exists only in tenant schemas, so an
 * insert would land in {@code public} and fail. This filter is also the single
 * clear point for {@link AuditRequestContext} — the masked body set by
 * {@link RequestBodyCaptureFilter} is consumed here and never leaks to the
 * next request on a reused thread.
 */
@Slf4j
@Component
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

            // Get masked request body from AuditRequestContext (high-risk paths only);
            // consuming it here is the single clear point (no leak across requests).
            String requestBody = AuditRequestContext.getAndClearRequestBody();

            if (TenantContext.getCurrentTenant().isEmpty()) {
                // No tenant resolved (actuator, tenant signup, unknown host): the table
                // lives in tenant schemas only — skip instead of failing the insert.
                return;
            }

            requestLogService.record(traceId, request.getMethod(), request.getRequestURI(), status, durationMs,
                    userId, username, ipAddress, userAgent, requestBody);
        }
    }
}
