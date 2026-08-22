package com.ibrhalil.forgesys.exception;

import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * Stable, machine-readable error codes surfaced in {@link ApiErrorResponse#code()}.
 * The wire value is the enum name lowercased (e.g. {@code auth_bad_credentials}).
 * Clients may branch on this value; the HTTP status and message may evolve but
 * codes remain stable.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
    BUSINESS_ERROR(HttpStatus.BAD_REQUEST, "Business rule violation"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Requested resource not found"),
    USER_EMAIL_TAKEN(HttpStatus.BAD_REQUEST, "Email is already in use"),
    USER_USERNAME_TAKEN(HttpStatus.BAD_REQUEST, "Username is already in use"),
    USER_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Current password is incorrect"),
    ROLE_NAME_TAKEN(HttpStatus.BAD_REQUEST, "Role name is already in use"),
    ROLE_PARENT_CYCLE(HttpStatus.BAD_REQUEST, "Cannot assign a parent role that would create an inheritance cycle"),
    GROUP_NAME_TAKEN(HttpStatus.BAD_REQUEST, "Group name is already in use"),
    PROJECT_NAME_TAKEN(HttpStatus.BAD_REQUEST, "Project name is already in use"),
    PERMISSION_NAME_TAKEN(HttpStatus.BAD_REQUEST, "Permission name is already in use"),
    PERMISSION_IN_USE(HttpStatus.CONFLICT, "Permission is still assigned to one or more roles; remove it from all roles before deleting"),
    COMPANY_SUBDOMAIN_TAKEN(HttpStatus.BAD_REQUEST, "Subdomain is already taken"),
    TENANT_NOT_FOUND(HttpStatus.BAD_REQUEST, "Tenant not found or inactive"),
    TENANT_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Invalid or unknown verification token"),
    TENANT_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "Verification token has expired"),
    TENANT_TOKEN_ALREADY_USED(HttpStatus.BAD_REQUEST, "Verification token has already been used"),
    AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    AUTH_BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
    AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account is temporarily locked after repeated failed login attempts"),
    AUTH_ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "Account is disabled"),
    AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"),
    AUTH_REFRESH_TOKEN_REUSE(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected; all sessions have been revoked"),
    AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied"),
    AUTH_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests; please slow down and retry later"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Session was not found or has already ended"),
    SELF_DELETE_FORBIDDEN(HttpStatus.CONFLICT, "You cannot delete your own account"),
    LAST_ADMIN_REQUIRED(HttpStatus.CONFLICT, "At least one active admin-capable user must remain in the tenant"),
    MODULE_NOT_FOUND(HttpStatus.NOT_FOUND, "Unknown module key"),
    MODULE_ALREADY_ACTIVE(HttpStatus.CONFLICT, "Module is already activated for this tenant"),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.CONFLICT, "Tenant has no active subscription"),
    MODULE_PLAN_REQUIRED(HttpStatus.FORBIDDEN, "Module requires a higher subscription plan"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public int statusCode() {
        return status.value();
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
