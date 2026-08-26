package com.ibrhalil.forgesys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reserved system-tenant admin bootstrap (K-24) under
 * {@code forgesys.bootstrap.system-admin.*}: a privileged but ordinary tenant user
 * (schema {@code tenant_<subdomain>} — the "no global users" rule holds). Dev defaults
 * in yaml; prod via {@code FORGESYS_BOOTSTRAP_SYSTEM_ADMIN_*} env. {@code enabled}
 * defaults false — prod is opt-in.
 */
@ConfigurationProperties(prefix = "forgesys.bootstrap.system-admin")
public record SystemAdminBootstrapProperties(
        boolean enabled,
        String companyName,
        String subdomain,
        String email,
        String password,
        String firstName,
        String lastName
) {
}
