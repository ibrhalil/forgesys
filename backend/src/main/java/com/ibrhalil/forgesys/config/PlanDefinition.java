package com.ibrhalil.forgesys.config;

/**
 * Code-side registry of subscription plans (K-16 / Epic 3.0.A). The single source of
 * truth for plan metadata — {@code PlanSyncRunner} upserts these into {@code t_plans}
 * (public schema) at startup. Financial flows (payment, upgrade/downgrade) arrive in
 * Faz 6; until then the rows are reference data gating module activation only.
 *
 * <p>{@link #rank} orders the plans (FREE &lt; PRO &lt; ENTERPRISE): a module's
 * {@code minPlan} rank gates activation — the tenant's plan rank must be &gt;= it.
 *
 * <p>Plan <em>limits</em> (K-15 / Epic 3.0.B) also live here — the code registry is the
 * single source of truth, so no {@code t_plans} migration is needed when limits change.
 * {@code -1} means unlimited. Enforcement is a soft-block (403 {@code app_limit_reached}):
 * creating above the limit is rejected, existing data is never hidden.
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
