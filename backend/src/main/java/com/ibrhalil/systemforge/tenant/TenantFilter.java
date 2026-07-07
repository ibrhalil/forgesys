package com.ibrhalil.systemforge.tenant;

import com.ibrhalil.systemforge.common.exception.TenantNotFoundException;
import com.ibrhalil.systemforge.common.tenant.TenantContext;
import com.ibrhalil.systemforge.entity.Company;
import com.ibrhalil.systemforge.entity.CompanyStatus;
import com.ibrhalil.systemforge.persistence.repository.CompanyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-ID";

    private final CompanyRepository companyRepository;
    private final String baseDomain;
    private final boolean isDevProfile;

    public TenantFilter(CompanyRepository companyRepository,
                        @Value("${systemforge.multi-tenancy.base-domain:localhost}") String baseDomain,
                        @Value("${spring.profiles.active:dev}") String activeProfiles) {
        this.companyRepository = companyRepository;
        this.baseDomain = baseDomain;
        this.isDevProfile = activeProfiles.contains("dev");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/") || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        try {
            String schemaName = resolveTenantSchema(request);
            if (schemaName != null) {
                TenantContext.setCurrentTenant(schemaName);
            }
        } catch (TenantNotFoundException ex) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveTenantSchema(HttpServletRequest request) {
        String subdomain = extractSubdomain(request.getHeader("Host"));
        if (StringUtils.hasText(subdomain)) {
            return resolveBySubdomain(subdomain);
        }
        if (isDevProfile) {
            String headerTenant = request.getHeader(TENANT_HEADER);
            if (StringUtils.hasText(headerTenant)) {
                return headerTenant;
            }
        }
        return null;
    }

    private String extractSubdomain(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String hostname = host.split(":")[0].toLowerCase();
        if ("localhost".equals(baseDomain) && hostname.endsWith(".localhost")) {
            return hostname.substring(0, hostname.length() - ".localhost".length());
        }
        if (hostname.endsWith("." + baseDomain)) {
            return hostname.substring(0, hostname.length() - ("." + baseDomain).length());
        }
        return null;
    }

    private String resolveBySubdomain(String subdomain) {
        return companyRepository.findBySubdomain(subdomain)
                .filter(company -> company.getStatus() == CompanyStatus.ACTIVE)
                .map(Company::getSchemaName)
                .orElseThrow(() -> new TenantNotFoundException(
                        "Tenant not found or inactive for subdomain: " + subdomain));
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"status":%d,"error":"Bad Request","message":"%s"}""".formatted(status, message));
    }
}
