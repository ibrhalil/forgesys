package com.ibrhalil.forgesys.service.mail;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds tenant-anchored action links: {@code {scheme}://{subdomain}.{host}[:{port}]{path}?token=...}
 * from {@code forgesys.security.app-base-url} (the frontend origin) — the link lands
 * on the tenant's own subdomain so {@code TenantFilter} resolves the schema when the
 * browser POSTs the token back. The subdomain derives from {@code tenant_<sub>}
 * (underscores folded from dashes at provisioning); the inverse fold is unique
 * because subdomains reject underscores.
 */
@Component
public class MailLinkBuilder {

    @Value("${forgesys.security.app-base-url:http://localhost:3000}")
    private String appBaseUrl;

    public String tenantLink(String schemaName, String path, String rawToken) {
        return tenantBaseUrl(schemaName) + path + "?token=" + rawToken;
    }

    /**
     * Origin of the tenant's own subdomain: {@code {scheme}://{subdomain}.{host}[:{port}]}.
     * K-50 F6: also the switch {@code targetUrl} base. A non-{@code tenant_*} schema
     * (single-schema H2 tests) folds to the bare base URL.
     */
    public String tenantBaseUrl(String schemaName) {
        URI base = URI.create(appBaseUrl);
        StringBuilder url = new StringBuilder()
                .append(base.getScheme()).append("://");
        if (schemaName != null && schemaName.startsWith("tenant_")) {
            String subdomain = schemaName.substring("tenant_".length()).replace('_', '-');
            url.append(subdomain).append('.');
        }
        url.append(base.getHost());
        if (base.getPort() != -1) {
            url.append(':').append(base.getPort());
        }
        return url.toString();
    }
}
