package com.ibrhalil.forgesys.entity;

/**
 * Tenant-module activation lifecycle (K-16 / Epic 3.0.A). Deactivation arrives with
 * plan-downgrade flows in Faz 6; activation currently only writes {@link #ACTIVE}.
 */
public enum ModuleStatus {
    ACTIVE
}
