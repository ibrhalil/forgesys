package com.ibrhalil.forgesys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Gates onboarding sample content for newly provisioned tenants (K-47); provisioning-only — existing tenants untouched. */
@ConfigurationProperties(prefix = "forgesys.provisioning.sample-data")
public record SampleDataProperties(
        @DefaultValue("true") boolean enabled
) {
}
