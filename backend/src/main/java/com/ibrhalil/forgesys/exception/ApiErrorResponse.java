package com.ibrhalil.forgesys.exception;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Uniform error response shape for every API error path (controller advice, security
 * handlers, tenant filter).
 *
 * @param status  HTTP status code
 * @param code    stable machine-readable code (see {@link ErrorCode}) — clients branch on this
 * @param message human-readable detail
 * @param path    request path
 * @param traceId per-request correlation id (MDC traceId, generated when absent)
 * @param fields  field-level violations (validation errors only; empty otherwise)
 */
public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        List<ApiFieldError> fields
) {
}
