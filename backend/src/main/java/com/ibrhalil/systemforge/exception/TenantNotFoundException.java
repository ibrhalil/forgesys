package com.ibrhalil.systemforge.exception;

/**
 * Exception thrown when a requested tenant is not found or is missing in the context.
 */
public class TenantNotFoundException extends RuntimeException {
    
    public TenantNotFoundException(String message) {
        super(message);
    }
}
