package com.ibrhalil.forgesys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sample data seeding configuration (K-47): {@code forgesys.provisioning.sample-data.enabled}
 * gates the onboarding sample content (the Linear pattern) created for a NEWLY provisioned
 * tenant. Provisioning-only — existing tenants are never touched, and a disabled flag
 * simply leaves new tenants empty (the test profile turns it off: fixtures are built
 * manually there).
 */
@ConfigurationProperties(prefix = "forgesys.provisioning.sample-data")
public record SampleDataProperties(
        @DefaultValue("true") boolean enabled
) {
}
