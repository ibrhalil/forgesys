package com.ibrhalil.forgesys.exception;

import com.ibrhalil.forgesys.common.exception.TenantNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler} — verifies the uniform
 * {@link ApiErrorResponse} mapping, {@link ErrorCode} selection, traceId
 * presence, and password/value sanitization. Pure unit tests (no web context):
 * Spring Boot 4.1 removed the {@code @WebMvcTest} slice from the standard
 * autoconfigure, and the handler is a thin mapper that needs no Spring context.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = request("/api/v1/users/42");

    @Test
    void authExceptionMapsToItsErrorCode() {
        ApiErrorResponse body = handler.handleBusiness(AuthException.badCredentials(), request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(401);
        assertThat(body.code()).isEqualTo("auth_bad_credentials");
        assertThat(body.message()).isEqualTo("Invalid username or password");
        assertThat(body.path()).isEqualTo("/api/v1/users/42");
        assertThat(body.traceId()).isNotBlank();
        assertThat(body.fields()).isEmpty();
    }

    @Test
    void resourceNotFoundMapsTo404() {
        ApiErrorResponse body = handler.handleResourceNotFound(new ResourceNotFoundException("User 42 not found"), request).getBody();

        assertThat(body.status()).isEqualTo(404);
        assertThat(body.code()).isEqualTo("resource_not_found");
        assertThat(body.message()).isEqualTo("User 42 not found");
    }

    @Test
    void tenantNotFoundMapsToTenantErrorCode() {
        ApiErrorResponse body = handler.handleTenantNotFound(new TenantNotFoundException("no such tenant"), request).getBody();

        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("tenant_not_found");
    }

    @Test
    void illegalArgumentMapsToBusinessError() {
        ApiErrorResponse body = handler.handleIllegalArgument(new IllegalArgumentException("bad arg"), request).getBody();

        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("business_error");
        assertThat(body.message()).isEqualTo("bad arg");
    }

    @Test
    void unhandledExceptionMapsToInternalError() {
        ApiErrorResponse body = handler.handleGeneralException(new RuntimeException("boom"), request).getBody();

        assertThat(body.status()).isEqualTo(500);
        assertThat(body.code()).isEqualTo("internal_error");
        assertThat(body.message()).doesNotContain("boom"); // never leaks raw stack detail
    }

    @Test
    void validationErrorReturnsFieldListAndMasksPassword() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "sampleDto");
        bindingResult.addError(fieldError("username", ""));
        bindingResult.addError(fieldError("password", "short"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ApiErrorResponse body = handler.handleValidationErrors(ex, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("validation_error");
        assertThat(body.fields()).hasSize(2);
        assertThat(field(body, "password").rejectedValue()).isEqualTo("[REDACTED]");
        assertThat(field(body, "username").rejectedValue()).isEqualTo("");
    }

    @Test
    void sanitizeMasksSensitiveFieldsOnly() {
        assertThat(GlobalExceptionHandler.sanitizeRejectedValue(fieldError("adminPassword", "secret"))).isEqualTo("[REDACTED]");
        assertThat(GlobalExceptionHandler.sanitizeRejectedValue(fieldError("refreshToken", "abc"))).isEqualTo("[REDACTED]");
        assertThat(GlobalExceptionHandler.sanitizeRejectedValue(fieldError("email", "a@b.com"))).isEqualTo("a@b.com");
    }

    @Test
    void typeMismatchMapsTo400Validation() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "not-a-uuid", UUID.class, "id", null, new IllegalArgumentException("bad uuid"));

        ApiErrorResponse body = handler.handleTypeMismatch(ex, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("validation_error");
        assertThat(body.message()).contains("id").contains("UUID");
    }

    @Test
    void missingParameterMapsTo400Validation() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("page", "int");

        ApiErrorResponse body = handler.handleMissingParam(ex, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("validation_error");
        assertThat(body.message()).contains("page");
    }

    @Test
    void constraintViolationMapsTo400AndMasksPassword() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<PasswordBean>> violations = validator.validate(new PasswordBean(""));
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        ApiErrorResponse body = handler.handleConstraintViolation(ex, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("validation_error");
        assertThat(field(body, "password").rejectedValue()).isEqualTo("[REDACTED]");
    }

    @Test
    void dataIntegrityKnownConstraintMapsToTakenCode() {
        org.hibernate.exception.ConstraintViolationException hibernate =
                new org.hibernate.exception.ConstraintViolationException("dup", new SQLException("dup"), "uk_users_email");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("dup", hibernate);

        ApiErrorResponse body = handler.handleDataIntegrity(ex, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("user_email_taken");
    }

    @Test
    void dataIntegrityUnknownConstraintMapsToBusinessError() {
        org.hibernate.exception.ConstraintViolationException hibernate =
                new org.hibernate.exception.ConstraintViolationException("dup", new SQLException("dup"), "uk_something_unknown");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("dup", hibernate);

        ApiErrorResponse body = handler.handleDataIntegrity(ex, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("business_error");
    }

    @SuppressWarnings("unused")
    private record PasswordBean(@NotBlank String password) {
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        return req;
    }

    private static FieldError fieldError(String field, Object rejectedValue) {
        return new FieldError("sampleDto", field, rejectedValue, false, null, null, "invalid");
    }

    private static ApiFieldError field(ApiErrorResponse body, String name) {
        return body.fields().stream()
                .filter(f -> f.field().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
