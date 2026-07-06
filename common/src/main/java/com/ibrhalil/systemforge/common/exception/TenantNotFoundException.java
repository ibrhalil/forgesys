package com.ibrhalil.systemforge.common.exception;

/**
 * Exception thrown when a requested tenant is not found or is missing in the context.
 * Shared across modules so the persistence layer can throw it and the web layer
 * can catch and map it to an HTTP response.
 */
public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(String message) {
        super(message);
    }
}
