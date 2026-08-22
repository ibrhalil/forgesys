package com.ibrhalil.forgesys.config;

/**
 * Code-side registry of subscription plans (K-16 / Epic 3.0.A). The single source of
 * truth for plan metadata — {@code PlanSyncRunner} upserts these into {@code t_plans}
 * (public schema) at startup. Financial flows (payment, upgrade/downgrade) arrive in
 * Faz 6; until then the rows are reference data gating module activation only.
 *
 * <p>{@link #rank} orders the plans (FREE &lt; PRO &lt; ENTERPRISE): a module's
 * {@code minPlan} rank gates activation — the tenant's plan rank must be &gt;= it.
 */
public enum PlanDefinition {

    FREE("free", "Free", 0),
    PRO("pro", "Pro", 1),
    ENTERPRISE("enterprise", "Enterprise", 2);

    private final String key;
    private final String displayName;
    private final int rank;

    PlanDefinition(String key, String displayName, int rank) {
        this.key = key;
        this.displayName = displayName;
        this.rank = rank;
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

    public boolean covers(PlanDefinition other) {
        return rank >= other.rank;
    }
}
