package com.ibrhalil.systemforge.exception;

import java.time.LocalDateTime;

/**
 * Standard error response DTO for API exceptions.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, LocalDateTime.now());
    }
}
