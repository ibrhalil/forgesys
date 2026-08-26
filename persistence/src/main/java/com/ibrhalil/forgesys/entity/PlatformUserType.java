package com.ibrhalil.forgesys.entity;

/**
 * Platform identity kind (K-50). HUMAN = superadmin (interactive, password login);
 * SERVICE = API agent (X-API-Key only, no password).
 */
public enum PlatformUserType {
    HUMAN,
    SERVICE
}
