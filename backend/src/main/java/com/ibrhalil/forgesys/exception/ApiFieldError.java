package com.ibrhalil.forgesys.exception;

/** A single field violation; {@code rejectedValue} is sanitized for sensitive fields. */
public record ApiFieldError(
        String field,
        Object rejectedValue,
        String message
) {
}
