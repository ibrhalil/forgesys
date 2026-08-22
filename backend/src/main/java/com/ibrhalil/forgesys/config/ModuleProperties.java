package com.ibrhalil.forgesys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Module system configuration (K-16 / Epic 3.0.A). {@code forgesys.modules.default-keys}
 * lists the modules every tenant gets activated at provisioning (and backfilled at
 * startup for pre-module-system tenants). Keys must exist in {@link ModuleDefinition};
 * unknown keys are logged and skipped by the consumers.
 */
@ConfigurationProperties(prefix = "forgesys.modules")
public record ModuleProperties(List<String> defaultKeys) {

    public static final List<String> DEFAULT_KEYS = List.of("pm");

    public List<String> effectiveDefaultKeys() {
        return defaultKeys == null || defaultKeys.isEmpty() ? DEFAULT_KEYS : List.copyOf(defaultKeys);
    }
}
