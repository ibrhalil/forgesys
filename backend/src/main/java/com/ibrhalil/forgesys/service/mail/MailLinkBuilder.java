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
        String subdomain = schemaName.substring("tenant_".length()).replace('_', '-');
        URI base = URI.create(appBaseUrl);
        StringBuilder url = new StringBuilder()
                .append(base.getScheme()).append("://")
                .append(subdomain).append('.').append(base.getHost());
        if (base.getPort() != -1) {
            url.append(':').append(base.getPort());
        }
        url.append(path).append("?token=").append(rawToken);
        return url.toString();
    }
}
