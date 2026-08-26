package com.ibrhalil.forgesys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code forgesys.modules.default-keys} (K-16): modules activated per tenant at
 * provisioning and backfilled at startup. Keys must exist in {@link ModuleDefinition};
 * unknown keys are logged + skipped.
 */
@ConfigurationProperties(prefix = "forgesys.modules")
public record ModuleProperties(List<String> defaultKeys) {

    public static final List<String> DEFAULT_KEYS = List.of("pm");

    public List<String> effectiveDefaultKeys() {
        return defaultKeys == null || defaultKeys.isEmpty() ? DEFAULT_KEYS : List.copyOf(defaultKeys);
    }
}
