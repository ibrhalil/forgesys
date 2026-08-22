package com.ibrhalil.forgesys.entity;

/**
 * Subscription lifecycle (K-16 / Epic 3.0.A). Financial flows (cancel/upgrade/downgrade)
 * arrive in Faz 6; provisioning currently only writes {@link #ACTIVE}.
 */
public enum SubscriptionStatus {
    ACTIVE
}
