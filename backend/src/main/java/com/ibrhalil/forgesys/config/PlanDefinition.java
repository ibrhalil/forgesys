package com.ibrhalil.forgesys.config;

/**
 * Code-side plan registry (K-16), upserted into {@code t_plans} at startup; financial
 * flows arrive in Faz 6. {@link #rank} orders plans (FREE &lt; PRO &lt; ENTERPRISE) and
 * gates module activation (tenant rank &gt;= module minPlan). Limits (K-15) live here —
 * no migration when they change; {@code -1} = unlimited, enforced as a soft-block
 * (403 {@code app_limit_reached}) that never hides existing data.
 */
public enum PlanDefinition {

    FREE("free", "Free", 0, 3, 1_000),
    PRO("pro", "Pro", 1, 25, 50_000),
    ENTERPRISE("enterprise", "Enterprise", 2, -1, -1);

    private final String key;
    private final String displayName;
    private final int rank;
    private final int maxApps;
    private final long maxRecordsPerApp;

    PlanDefinition(String key, String displayName, int rank, int maxApps, long maxRecordsPerApp) {
        this.key = key;
        this.displayName = displayName;
        this.rank = rank;
        this.maxApps = maxApps;
        this.maxRecordsPerApp = maxRecordsPerApp;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int rank() {
        return rank;
    }

    /** Max custom apps per tenant; {@code -1} = unlimited (soft-block, K-15). */
    public int maxApps() {
        return maxApps;
    }

    /** Max records per custom app; {@code -1} = unlimited (soft-block, K-15). */
    public long maxRecordsPerApp() {
        return maxRecordsPerApp;
    }

    public boolean covers(PlanDefinition other) {
        return rank >= other.rank;
    }
}
