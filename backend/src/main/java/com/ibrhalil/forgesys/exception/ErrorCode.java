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
    GROUP_NAME_TAKEN(HttpStatus.BAD_REQUEST, "Group name is already in use"),
    TENANT_NOT_FOUND(HttpStatus.BAD_REQUEST, "Tenant not found or inactive"),
    AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    AUTH_BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Invalid authentication token"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Authentication token has expired"),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Authentication token has been revoked"),
    AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied"),
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
