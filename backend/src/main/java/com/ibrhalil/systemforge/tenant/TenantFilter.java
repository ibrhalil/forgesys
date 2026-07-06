package com.ibrhalil.systemforge.tenant;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter to intercept HTTP requests and extract the tenant identifier from request headers.
 */
@Component
public class TenantFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest) {
            String tenantId = httpRequest.getHeader(TENANT_HEADER);
            if (tenantId != null && !tenantId.trim().isEmpty()) {
                TenantContext.setCurrentTenant(tenantId);
            } else {
                log.debug("No tenant identifier found in request header: {}", TENANT_HEADER);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // Always clean up thread local to prevent memory leaks in servlet threads
            TenantContext.clear();
        }
    }
}
