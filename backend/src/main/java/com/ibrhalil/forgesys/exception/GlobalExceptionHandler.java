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

    /**
     * No handler mapping matched the request path (Spring 6.1+ static-resource chain
     * throws {@link NoResourceFoundException} instead of rendering a plain 404). Maps
     * to the standard {@code resource_not_found} wire shape — e.g. hitting
     * {@code /v3/api-docs} in a profile where springdoc is disabled (K-41 prod gating).
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

    /**
     * Malformed path/query parameter that cannot be converted to its target type
     * (e.g. {@code GET /api/v1/users/not-a-uuid}). Without this the catch-all maps it
     * to 500; it is a client error, so it maps to {@code validation_error} (400).
     * [RISK-29]
     */
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

    /**
     * Missing required {@code @RequestParam} (e.g. {@code GET /api/v1/users?}). [RISK-29]
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Missing required request parameter: '" + ex.getParameterName() + "'";
        log.warn("Missing parameter at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, message, request.getRequestURI());
    }

    /**
     * A {@code sort} property that does not resolve against the domain entity (e.g.
     * {@code GET /api/v1/users?sort=notAField}). Without this the repository layer
     * propagates the Spring Data exception into the catch-all and the client gets a
     * 500; it is a client error, so it maps to {@code validation_error} (400). Known
     * property <em>paths</em> that should stay closed are rejected earlier by
     * {@code SortGuard} at the controller.
     */
    @ExceptionHandler(org.springframework.data.core.PropertyReferenceException.class)
    public ResponseEntity<ApiErrorResponse> handlePropertyReference(org.springframework.data.core.PropertyReferenceException ex, HttpServletRequest request) {
        String message = "Unsupported sort property: '" + ex.getPropertyName() + "'";
        log.warn("Bad sort property at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, message, request.getRequestURI());
    }

    /**
     * Bean Validation violations on method parameters (service-layer {@code @Validated}
     * beans, or a future controller {@code @Validated}). Currently nothing triggers this —
     * controllers use {@code @Valid} on {@code @RequestBody} which yields
     * {@link MethodArgumentNotValidException}; kept as a defensive, forward-compatible
     * 400 path. Sensitive rejected values are masked. [RISK-29]
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
     * Concurrent uniqueness race (TOCTOU): two requests pass the service-level
     * {@code existsBy*} check and one hits the DB unique constraint. Without this it
     * surfaces as a generic 500; mapped to 400 with the precise {@code *_TAKEN} code
     * when the constraint name is recognized (PostgreSQL partial-unique index names),
     * otherwise {@code business_error}. The service checks stay (defense-in-depth);
     * this covers the race. [RISK-28]
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
     * Backing-store connectivity failure (Redis/DB down — e.g. a refresh-token
     * {@code issue} that cannot persist). Surfaced as 503 {@code service_unavailable}
     * instead of a generic 500 so clients can distinguish "retry later" from a real
     * bug. {@link DataIntegrityViolationException} keeps its more specific handler.
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

    /**
     * Masks the rejected value of sensitive fields (password, token, secret,
     * credential) so secrets are never echoed back in a validation error payload.
     */
    static Object sanitizeRejectedValue(FieldError fieldError) {
        return maskIfSensitive(fieldError.getField(), fieldError.getRejectedValue());
    }

    /**
     * Shared sensitive-value masking for both {@code @RequestBody} field errors and
     * {@code ConstraintViolation} invalid values.
     */
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
     * Maps a unique-constraint violation to a precise {@code *_TAKEN} {@link ErrorCode}
     * by substring-matching the constraint/index name. Substring matching is portable
     * across PostgreSQL (named partial-unique indexes {@code uk_users_email} etc.) and
     * tolerates H2/PG name divergence; unknown constraints fall back to
     * {@link ErrorCode#BUSINESS_ERROR} (still 400, never 500).
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
        if (lower.contains("apps_name")) return ErrorCode.APP_NAME_TAKEN;
        if (lower.contains("app_properties_name")) return ErrorCode.APP_PROPERTY_NAME_TAKEN;
        if (lower.contains("app_views_name")) return ErrorCode.APP_VIEW_NAME_TAKEN;
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
