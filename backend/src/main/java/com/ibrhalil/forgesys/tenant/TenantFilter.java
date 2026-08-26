package com.ibrhalil.forgesys.tenant;

import com.ibrhalil.forgesys.common.exception.TenantNotFoundException;
import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.exception.ApiErrorFactory;
import com.ibrhalil.forgesys.exception.ApiErrorResponse;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-ID";

    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;
    private final String baseDomain;
    private final boolean isDevProfile;

    public TenantFilter(CompanyRepository companyRepository,
                        ObjectMapper objectMapper,
                        @Value("${forgesys.multi-tenancy.base-domain:localhost}") String baseDomain,
                        @Value("${spring.profiles.active:dev}") String activeProfiles) {
        this.companyRepository = companyRepository;
        this.objectMapper = objectMapper;
        this.baseDomain = baseDomain;
        this.isDevProfile = activeProfiles.contains("dev");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only tenant-creation endpoints lack a tenant context; login and /me ARE
        // tenant-specific and go through normal subdomain resolution.
        return path.startsWith("/api/v1/auth/company/") || path.startsWith("/actuator/");
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
            sendErrorResponse(response, request, ex.getMessage());
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
                return resolveBySubdomain(headerTenant);
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

    private void sendErrorResponse(HttpServletResponse response, HttpServletRequest request, String message) throws IOException {
        ApiErrorResponse body = ApiErrorFactory.of(
                ErrorCode.TENANT_NOT_FOUND,
                message,
                request.getRequestURI()
        );
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
