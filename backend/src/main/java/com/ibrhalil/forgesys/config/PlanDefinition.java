package com.ibrhalil.forgesys.config;

import java.util.Arrays;
import java.util.Optional;

/**
 * Code-side plan registry (K-16), upserted into {@code t_plans} at startup; financial
 * flows arrive in Faz 6. {@link #rank} orders plans (FREE &lt; PRO &lt; ENTERPRISE) and
 * gates module activation (tenant rank &gt;= module minPlan). Limits (K-15) live here —
 * no migration when they change; {@code -1} = unlimited, enforced as a soft-block
 * (403 {@code custom_app_limit_reached}) that never hides existing data.
 */
public enum PlanDefinition {

    FREE("free", "Free", 0, 3, 1_000),
    PRO("pro", "Pro", 1, 25, 50_000),
    ENTERPRISE("enterprise", "Enterprise", 2, -1, -1);

    private final String key;
    private final String displayName;
    private final int rank;
    private final int maxCustomApps;
    private final long maxRecordsPerCustomApp;

    PlanDefinition(String key, String displayName, int rank, int maxCustomApps, long maxRecordsPerCustomApp) {
        this.key = key;
        this.displayName = displayName;
        this.rank = rank;
        this.maxCustomApps = maxCustomApps;
        this.maxRecordsPerCustomApp = maxRecordsPerCustomApp;
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
    public int maxCustomApps() {
        return maxCustomApps;
    }

    /** Max records per custom app; {@code -1} = unlimited (soft-block, K-15). */
    public long maxRecordsPerCustomApp() {
        return maxRecordsPerCustomApp;
    }

    public boolean covers(PlanDefinition other) {
        return rank >= other.rank;
    }

    /** Registry lookup by {@code t_plans} key; empty for unknown keys. */
    public static Optional<PlanDefinition> fromKey(String key) {
        return Arrays.stream(values())
                .filter(definition -> definition.key.equals(key))
                .findFirst();
    }
}
