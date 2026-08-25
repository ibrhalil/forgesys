package com.ibrhalil.forgesys.entity;

/**
 * Purpose of a {@link UserAuthToken} — decides the TTL and the consuming flow.
 */
public enum UserAuthTokenPurpose {

    /** Tenant-internal user email verification (optional-policy flow). */
    EMAIL_VERIFY,

    /** Self-service password reset ({@code forgot-password}/{@code reset-password}). */
    PASSWORD_RESET
}
