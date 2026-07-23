package com.ibrhalil.systemforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap configuration for the reserved {@code system} tenant admin user.
 *
 * <p>At startup {@link SystemAdminBootstrapRunner} provisions (idempotently) a
 * dedicated tenant ({@code tenant_<subdomain>} schema) with an admin user whose
 * credentials come from these properties. The system admin is the privileged
 * identity used for platform operations and as a service account for M2M/job
 * outbound calls. It is a normal tenant user in a reserved schema — there is no
 * global user (AGENTS "no global users" rule is preserved).
 *
 * <p>Profile policy: dev defaults live in {@code application-dev.yaml}; in prod
 * the same keys are supplied via environment ({@code SYSTEMFORGE_BOOTSTRAP_SYSTEM_ADMIN_*}).
 * In the {@code test} profile the runner is not created at all. {@code enabled}
 * defaults to {@code false} (primitive), so prod stays opt-in unless explicitly
 * enabled.
 */
@ConfigurationProperties(prefix = "systemforge.bootstrap.system-admin")
public record SystemAdminBootstrapProperties(
        boolean enabled,
        String companyName,
        String subdomain,
        String emailDomain,
        String email,
        String password,
        String firstName,
        String lastName
) {
}
