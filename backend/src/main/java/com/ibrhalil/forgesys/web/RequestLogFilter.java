package com.ibrhalil.forgesys.web;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
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
 * Writes a {@code t_request_logs} row after the request completes (K-19 layer 3).
 * Order -95, INSIDE the security chain: this filter's {@code finally} unwinds
 * BEFORE the tenant/security/metadata filters clear their ThreadLocals, so tenant
 * schema, authentication and request metadata are still live at write time. Also
 * the single clear point for {@link AuditRequestContext}.
 * rationale: docs/CODE_NOTES.md (backend/web → RequestLogFilter)
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

            UUID userId = null;
            String username = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.ibrhalil.forgesys.security.CustomUserDetails user) {
                userId = user.getUserId();
                username = user.getEmail();
            }

            String ipAddress = null;
            String userAgent = null;
            var meta = RequestContext.current();
            if (meta.isPresent()) {
                ipAddress = meta.get().clientIp();
                userAgent = meta.get().userAgent();
            }

            // Consuming here is the single clear point — no leak across reused threads.
            String requestBody = AuditRequestContext.getAndClearRequestBody();

            if (TenantContext.getCurrentTenant().isEmpty()) {
                // Table lives in tenant schemas only — skip instead of failing the insert.
                return;
            }

            requestLogService.record(traceId, request.getMethod(), request.getRequestURI(), status, durationMs,
                    userId, username, ipAddress, userAgent, requestBody);
        }
    }
}
