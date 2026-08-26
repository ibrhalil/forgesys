package com.ibrhalil.forgesys.entity;

import java.util.EnumSet;
import java.util.Map;

/**
 * Company lifecycle. Manual platform-admin transitions are constrained by
 * {@link #canTransitionTo} (RISK-32): {@code PROVISIONING} completes only via the
 * verify flow (never a manual flip), {@code TERMINATED} is terminal.
 */
public enum CompanyStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    TERMINATED;

    private static final Map<CompanyStatus, EnumSet<CompanyStatus>> ALLOWED_TRANSITIONS = Map.of(
            PROVISIONING, EnumSet.noneOf(CompanyStatus.class),
            ACTIVE, EnumSet.of(SUSPENDED, TERMINATED),
            SUSPENDED, EnumSet.of(ACTIVE, TERMINATED),
            TERMINATED, EnumSet.noneOf(CompanyStatus.class)
    );

    /** Whether the platform-admin manual status update may move from {@code this} to {@code target}. */
    public boolean canTransitionTo(CompanyStatus target) {
        if (target == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(CompanyStatus.class)).contains(target);
    }
}
