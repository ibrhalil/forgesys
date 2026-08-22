package com.ibrhalil.forgesys.dto;

/**
 * Module catalog entry enriched with the current tenant's activation state (K-16 /
 * Epic 3.0.A). {@code active} = an ACTIVE row exists in {@code t_tenant_modules};
 * {@code allowedByPlan} = the tenant's plan rank meets the module's minimum plan.
 */
public record ModuleResponse(
        String key,
        String name,
        String minPlan,
        boolean active,
        boolean allowedByPlan) {
}
