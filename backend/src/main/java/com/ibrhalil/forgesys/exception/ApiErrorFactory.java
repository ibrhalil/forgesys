package com.ibrhalil.forgesys.exception;

import org.slf4j.MDC;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Builds {@link ApiErrorResponse} instances from {@link ErrorCode}s.
 *
 * <p>The {@code traceId} is resolved from the MDC, populated per request by
 * {@code com.ibrhalil.forgesys.web.RequestMetadataFilter} (which honors the
 * {@code X-Request-Id} header or generates a UUID). Outside a request thread
 * (or before the filter has run) a fresh UUID is generated so the field is never
 * null and remains useful for log correlation.
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
