package com.ibrhalil.forgesys.exception;

import com.ibrhalil.forgesys.common.exception.TenantNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;

/**
 * Global REST exception handler. Maps every exception to the uniform
 * {@link ApiErrorResponse} shape with a stable {@link ErrorCode}, traceId, and
 * sanitized field errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String REDACTED = "[REDACTED]";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception [{}]: {} at {}", ex.errorCode().code(), ex.getMessage(), request.getRequestURI());
        return build(ex.errorCode(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} at {}", ex.getMessage(), request.getRequestURI());
        return build(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTenantNotFound(TenantNotFoundException ex, HttpServletRequest request) {
        log.warn("Tenant not found: {} at {}", ex.getMessage(), request.getRequestURI());
        return build(ErrorCode.TENANT_NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {} at {}", ex.getMessage(), request.getRequestURI());
        return build(ErrorCode.BUSINESS_ERROR, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Method-level {@code @PreAuthorize} denials throw {@code AuthorizationDeniedException}
     * (a subclass of {@link AccessDeniedException}) from the controller method, so they
     * surface at the MVC layer and are caught here rather than by the filter-chain
     * {@code RestAccessDeniedHandler}. Mapped to 403 to match the wire contract.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.AUTH_ACCESS_DENIED, ErrorCode.AUTH_ACCESS_DENIED.defaultMessage(), request.getRequestURI());
    }

    /**
     * Malformed JSON body (e.g. an unparseable/invalid enum value like
     * {@code {"status":"FOO"}}). Without this the catch-all maps it to 500; the body is
     * a client error, so it maps to {@code validation_error} (400).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, "Malformed request body", request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiFieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiFieldError(fe.getField(), sanitizeRejectedValue(fe), fe.getDefaultMessage()))
                .toList();
        ApiErrorResponse body = ApiErrorFactory.of(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request.getRequestURI(),
                fields
        );
        log.warn("Validation failed [{}]: {} field error(s) at {}", body.traceId(), fields.size(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at path: {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request.getRequestURI());
    }

    private static ResponseEntity<ApiErrorResponse> build(ErrorCode errorCode, String message, String path) {
        return ResponseEntity.status(errorCode.status()).body(ApiErrorFactory.of(errorCode, message, path));
    }

    /**
     * Masks the rejected value of sensitive fields (password, token, secret,
     * credential) so secrets are never echoed back in a validation error payload.
     */
    static Object sanitizeRejectedValue(FieldError fieldError) {
        String field = fieldError.getField() == null ? "" : fieldError.getField().toLowerCase();
        if (field.contains("password") || field.contains("token")
                || field.contains("secret") || field.contains("credential")) {
            return REDACTED;
        }
        return fieldError.getRejectedValue();
    }
}
