package com.ibrhalil.forgesys.exception;

import com.ibrhalil.forgesys.common.exception.TenantNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;
import java.util.Locale;

/**
 * Global REST exception handler: every exception → uniform {@link ApiErrorResponse}
 * with a stable {@link ErrorCode}, traceId and sanitized field errors.
 * Rationale: docs/CODE_NOTES.md (backend → exception package).
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

    /**
     * No handler matched the path (Spring 6.1+ {@link NoResourceFoundException}) → 404
     * {@code resource_not_found} (e.g. springdoc disabled in prod, K-41).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("No resource found: {} at {}", ex.getMessage(), request.getRequestURI());
        return build(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint found for this path",
                request.getRequestURI());
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
     * Method-level {@code @PreAuthorize} denials ({@code AuthorizationDeniedException})
     * surface at the MVC layer → 403 here, not in the filter-chain handler.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.AUTH_ACCESS_DENIED, ErrorCode.AUTH_ACCESS_DENIED.defaultMessage(), request.getRequestURI());
    }

    /** Malformed JSON body → 400 {@code validation_error} (not a 500). */
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

    /** Malformed path/query param → 400 {@code validation_error} ([RISK-29]). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String param = ex.getName();
        Class<?> required = ex.getRequiredType();
        String expected = required != null ? required.getSimpleName() : "the expected type";
        String message = (param != null && !param.isBlank())
                ? "Malformed request parameter '" + param + "': expected " + expected
                : "Malformed request parameter: expected " + expected;
        log.warn("Type mismatch at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, message, request.getRequestURI());
    }

    /** Missing required {@code @RequestParam} → 400 {@code validation_error} ([RISK-29]). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Missing required request parameter: '" + ex.getParameterName() + "'";
        log.warn("Missing parameter at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, message, request.getRequestURI());
    }

    /**
     * Unknown {@code sort} property → 400 {@code validation_error} ([RISK-29]); closed
     * property paths are rejected earlier by {@code SortGuard} at the controller.
     */
    @ExceptionHandler(org.springframework.data.core.PropertyReferenceException.class)
    public ResponseEntity<ApiErrorResponse> handlePropertyReference(org.springframework.data.core.PropertyReferenceException ex, HttpServletRequest request) {
        String message = "Unsupported sort property: '" + ex.getPropertyName() + "'";
        log.warn("Bad sort property at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, message, request.getRequestURI());
    }

    /**
     * Bean Validation on method parameters → 400; defensive/forward-compatible
     * (controllers use {@code @Valid} → {@link MethodArgumentNotValidException}).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiFieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new ApiFieldError(leafProperty(v.getPropertyPath()), maskIfSensitive(leafProperty(v.getPropertyPath()), v.getInvalidValue()), v.getMessage()))
                .toList();
        ApiErrorResponse body = ApiErrorFactory.of(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request.getRequestURI(),
                fields);
        log.warn("Constraint violations [{}]: {} field error(s) at {}", body.traceId(), fields.size(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Concurrent uniqueness race → 400 with the precise {@code *_TAKEN} code when the
     * constraint name is recognized, else {@code business_error} ([RISK-28]).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        ErrorCode code = resolveDuplicateCode(ex);
        String message = code == ErrorCode.BUSINESS_ERROR
                ? "A record with these values already exists"
                : code.defaultMessage();
        log.warn("Data integrity violation [{}] at {}: {}", code.code(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(code, message, request.getRequestURI());
    }

    /**
     * Backing-store failure (Redis/DB down) → 503 {@code service_unavailable} —
     * "retry later", not a bug. {@link DataIntegrityViolationException} keeps its own handler.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Data access failure at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.defaultMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at path: {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request.getRequestURI());
    }

    private static ResponseEntity<ApiErrorResponse> build(ErrorCode errorCode, String message, String path) {
        return ResponseEntity.status(errorCode.status()).body(ApiErrorFactory.of(errorCode, message, path));
    }

    /** Masks sensitive rejected values (password/token/secret/credential). */
    static Object sanitizeRejectedValue(FieldError fieldError) {
        return maskIfSensitive(fieldError.getField(), fieldError.getRejectedValue());
    }

    /** Shared masking for field errors and {@code ConstraintViolation} invalid values. */
    private static Object maskIfSensitive(String fieldName, Object value) {
        String field = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        if (field.contains("password") || field.contains("token")
                || field.contains("secret") || field.contains("credential")) {
            return REDACTED;
        }
        return value;
    }

    /** Leaf property name of a Bean Validation path (e.g. {@code doIt.arg0} -> {@code arg0}). */
    private static String leafProperty(Path path) {
        String leaf = null;
        if (path != null) {
            for (Path.Node node : path) {
                leaf = node.getName();
            }
        }
        return leaf == null ? "" : leaf;
    }

    /**
     * Maps a unique-constraint violation to a {@code *_TAKEN} code by constraint-name
     * substring (portable across PG/H2); unknown constraints → {@code business_error}
     * (still 400, never 500).
     */
    private ErrorCode resolveDuplicateCode(DataIntegrityViolationException ex) {
        String constraintName = extractConstraintName(ex);
        if (constraintName == null) {
            return ErrorCode.BUSINESS_ERROR;
        }
        String lower = constraintName.toLowerCase(Locale.ROOT);
        if (lower.contains("users_email")) return ErrorCode.USER_EMAIL_TAKEN;
        if (lower.contains("users_username")) return ErrorCode.USER_USERNAME_TAKEN;
        if (lower.contains("roles_name")) return ErrorCode.ROLE_NAME_TAKEN;
        if (lower.contains("groups_name")) return ErrorCode.GROUP_NAME_TAKEN;
        if (lower.contains("permissions_name")) return ErrorCode.PERMISSION_NAME_TAKEN;
        if (lower.contains("projects_name") || lower.contains("projects_type_name")) return ErrorCode.PROJECT_NAME_TAKEN;
        if (lower.contains("note_categories_name")) return ErrorCode.NOTE_CATEGORY_NAME_TAKEN;
        if (lower.contains("custom_apps_name")) return ErrorCode.CUSTOM_APP_NAME_TAKEN;
        if (lower.contains("custom_app_properties_name")) return ErrorCode.CUSTOM_APP_PROPERTY_NAME_TAKEN;
        if (lower.contains("custom_app_views_name")) return ErrorCode.CUSTOM_APP_VIEW_NAME_TAKEN;
        if (lower.contains("tenant_modules_company_module")) return ErrorCode.MODULE_ALREADY_ACTIVE;
        if (lower.contains("companies_subdomain") || lower.contains("companies_schema_name")) {
            return ErrorCode.COMPANY_SUBDOMAIN_TAKEN;
        }
        return ErrorCode.BUSINESS_ERROR;
    }

    /** Unwraps the Hibernate constraint name from the exception cause chain. */
    private String extractConstraintName(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException hibernate) {
                String name = hibernate.getConstraintName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
            cause = cause.getCause();
        }
        return null;
    }
}
