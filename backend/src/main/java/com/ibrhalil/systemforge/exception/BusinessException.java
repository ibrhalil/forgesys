package com.ibrhalil.systemforge.exception;

/**
 * Business/domain exceptions that carry a stable {@link ErrorCode}. Concrete subclasses
 * ({@link AuthException}, {@link ResourceNotFoundException}) model well-known categories,
 * but a {@code BusinessException} may also be thrown directly with a specific
 * {@link ErrorCode} (e.g. {@code USER_EMAIL_TAKEN}) for one-off business rule violations.
 * All variants are mapped to the uniform {@link ApiErrorResponse} by
 * {@link GlobalExceptionHandler}.
 *
 * <p>This hierarchy lives in the {@code backend} module (not {@code common})
 * because it references Spring's HTTP status types, which are forbidden in the
 * Spring-free {@code common} module. Cross-module exceptions such as
 * {@code TenantNotFoundException} remain plain {@code RuntimeException}s and are
 * translated to an {@code ErrorCode} by the handler.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
