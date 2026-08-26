package com.ibrhalil.forgesys.exception;

/**
 * Business/domain exception carrying a stable {@link ErrorCode}; may also be thrown
 * directly with a specific code for one-off rules. Lives in {@code backend} (it
 * references Spring HTTP types — forbidden in the Spring-free {@code common} module);
 * cross-module exceptions stay plain {@code RuntimeException}s translated by the handler.
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
