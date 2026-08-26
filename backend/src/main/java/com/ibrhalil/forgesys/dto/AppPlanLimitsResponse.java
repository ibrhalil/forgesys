package com.ibrhalil.forgesys.dto;

/**
 * The tenant's plan limits for the App Builder usage indicators (K-42); values
 * come from the {@code PlanDefinition} registry. {@code -1} means unlimited.
 */
public record AppPlanLimitsResponse(
        String planKey,
        String planName,
        int maxApps,
        long maxRecordsPerApp) {
}
