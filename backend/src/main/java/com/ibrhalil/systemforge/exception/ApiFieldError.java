package com.ibrhalil.systemforge.exception;

/**
 * A single bean-validation field violation. The {@code rejectedValue} is
 * sanitized for sensitive fields (password/token/secret) before being exposed.
 */
public record ApiFieldError(
        String field,
        Object rejectedValue,
        String message
) {
}
