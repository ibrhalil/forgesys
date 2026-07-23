package com.ibrhalil.systemforge.exception;

/**
 * Base class for business/domain exceptions that carry a stable {@link ErrorCode}.
 * Subclasses are mapped to the uniform {@link ApiErrorResponse} by
 * {@link GlobalExceptionHandler}.
 *
 * <p>This hierarchy lives in the {@code backend} module (not {@code common})
 * because it references Spring's HTTP status types, which are forbidden in the
 * Spring-free {@code common} module. Cross-module exceptions such as
 * {@code TenantNotFoundException} remain plain {@code RuntimeException}s and are
 * translated to an {@code ErrorCode} by the handler.
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
