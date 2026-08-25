package com.ibrhalil.forgesys.service.mail;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds tenant-anchored action links for mailed flows (email verification, password
 * reset): {@code {scheme}://{subdomain}.{host}[:{port}]{path}?token=...}. Derived from
 * {@code forgesys.security.app-base-url} — the frontend origin — by prefixing the
 * tenant subdomain onto its host, so the link lands on the tenant's own subdomain and
 * {@code TenantFilter} resolves the schema when the browser POSTs the token back.
 *
 * <p>The subdomain is derived from the tenant schema name ({@code tenant_<sub>} with
 * dashes folded to underscores at provisioning); the inverse fold is unique because
 * subdomains reject underscores.
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
