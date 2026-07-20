package com.ibrhalil.systemforge.common.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * ThreadLocal context class to store and retrieve the current tenant identifier.
 * Shared between the web layer (TenantFilter sets it) and the persistence layer
 * (multi-tenant connection provider reads it).
 */
public final class TenantContext {

    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    private TenantContext() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void setCurrentTenant(String tenantId) {
        log.debug("Setting current tenant context to: {}", tenantId);
        currentTenant.set(tenantId);
    }

    public static Optional<String> getCurrentTenant() {
        return Optional.ofNullable(currentTenant.get());
    }

    public static void clear() {
        log.debug("Clearing tenant context");
        currentTenant.remove();
    }
}
