package com.ibrhalil.forgesys.exception;

import org.slf4j.MDC;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Builds {@link ApiErrorResponse} instances. {@code traceId} comes from the MDC
 * (populated per request by {@code RequestMetadataFilter}); a fresh UUID is generated
 * outside a request thread so the field is never null.
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
