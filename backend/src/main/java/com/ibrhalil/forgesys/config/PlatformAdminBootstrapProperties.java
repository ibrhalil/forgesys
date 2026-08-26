package com.ibrhalil.forgesys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * K-50 platform superadmin bootstrap under {@code forgesys.bootstrap.platform-admin.*}:
 * creates the initial HUMAN {@code t_platform_users} row at startup. No self-signup
 * exists — this is the only way a platform identity comes into being besides a
 * superadmin creating service accounts. Dev defaults in yaml; prod via
 * {@code FORGESYS_BOOTSTRAP_PLATFORM_ADMIN_*} env. {@code enabled} defaults false —
 * prod is opt-in.
 */
@ConfigurationProperties(prefix = "forgesys.bootstrap.platform-admin")
public record PlatformAdminBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String displayName
) {
}
