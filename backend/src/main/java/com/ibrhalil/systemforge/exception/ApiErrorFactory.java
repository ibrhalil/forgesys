package com.ibrhalil.systemforge.exception;

import org.slf4j.MDC;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Builds {@link ApiErrorResponse} instances from {@link ErrorCode}s.
 *
 * <p>The {@code traceId} is resolved from the MDC ({@code RequestLoggingFilter}
 * sets it in a later chunk); when no filter has populated it yet, a fresh UUID
 * is generated so the field is never null and remains useful for log correlation.
 * This keeps the error shape forward-compatible with request/trace logging.
 */
public final class ApiErrorFactory {

    public static final String TRACE_ID_KEY = "traceId";

    private ApiErrorFactory() {
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message, String path) {
        return of(errorCode, message, path, List.of());
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message, String path, List<ApiFieldError> fields) {
        return new ApiErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                errorCode.statusCode(),
                errorCode.status().name(),
                errorCode.code(),
                message,
                path,
                resolveTraceId(),
                fields == null ? List.of() : fields
        );
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode, errorCode.defaultMessage(), path);
    }

    private static String resolveTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
