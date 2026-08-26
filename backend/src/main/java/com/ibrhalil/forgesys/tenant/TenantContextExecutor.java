package com.ibrhalil.forgesys.tenant;

import com.ibrhalil.forgesys.common.tenant.TenantContext;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs an action inside a tenant schema context, restoring the caller's context
 * afterward. The single sanctioned switch primitive — hand-rolled set/clear
 * pairs leak the tenant across pooled threads ([RISK-10]).
 */
public final class TenantContextExecutor {

    private TenantContextExecutor() {
    }

    public static <T> T inTenantContext(String schemaName, Supplier<T> action) {
        Optional<String> previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(schemaName);
        try {
            return action.get();
        } finally {
            previous.ifPresentOrElse(TenantContext::setCurrentTenant, TenantContext::clear);
        }
    }

    public static void inTenantContext(String schemaName, Runnable action) {
        inTenantContext(schemaName, () -> {
            action.run();
            return null;
        });
    }
}
