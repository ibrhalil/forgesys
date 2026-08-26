package com.ibrhalil.forgesys.dto;

/**
 * Module catalog entry with the tenant's activation state (K-16):
 * {@code active} = ACTIVE row in {@code t_tenant_modules}; {@code allowedByPlan}
 * = plan rank meets the module's minimum plan.
 */
public record ModuleResponse(
        String key,
        String name,
        String minPlan,
        boolean active,
        boolean allowedByPlan) {
}
