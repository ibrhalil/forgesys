package com.ibrhalil.forgesys.dto;

/**
 * The current tenant's plan limits for the App Builder UI usage indicators (K-42 /
 * Epic 4.2). Values come straight from the code-side {@code PlanDefinition} registry
 * through {@code PlanLimitService.activePlan()} — the single plan-resolution chain;
 * nothing is hardcoded client-side. {@code -1} means unlimited.
 */
public record AppPlanLimitsResponse(
        String planKey,
        String planName,
        int maxApps,
        long maxRecordsPerApp) {
}
