package com.ibrhalil.forgesys.dto;

/**
 * The tenant's plan limits for the CustomApp Builder usage indicators (K-42); values
 * come from the {@code PlanDefinition} registry. {@code -1} means unlimited.
 */
public record CustomAppPlanLimitsResponse(
        String planKey,
        String planName,
        int maxCustomApps,
        long maxRecordsPerCustomApp) {
}
