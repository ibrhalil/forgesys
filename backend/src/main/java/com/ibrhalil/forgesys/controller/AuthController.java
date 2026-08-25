package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.dto.CompanyRegisterResponse;
import com.ibrhalil.forgesys.dto.CompanyVerifyRequest;
import com.ibrhalil.forgesys.dto.CompanyVerifyResponse;
import com.ibrhalil.forgesys.dto.EmailVerificationResponse;
import com.ibrhalil.forgesys.dto.EmailVerifyRequest;
import com.ibrhalil.forgesys.dto.ForgotPasswordRequest;
import com.ibrhalil.forgesys.dto.LoginRequest;
import com.ibrhalil.forgesys.dto.LoginResponse;
import com.ibrhalil.forgesys.dto.RefreshRequest;
import com.ibrhalil.forgesys.dto.ResetPasswordRequest;
import com.ibrhalil.forgesys.dto.SubdomainSuggestionRequest;
import com.ibrhalil.forgesys.dto.SubdomainSuggestionResponse;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.jwt.JwtCookieProperties;
import com.ibrhalil.forgesys.service.AuthService;
import com.ibrhalil.forgesys.service.SubdomainSuggestionService;
import com.ibrhalil.forgesys.service.TenantProvisioningService;
import com.ibrhalil.forgesys.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TenantProvisioningService tenantProvisioningService;
    private final AuthService authService;
    private final UserService userService;
    private final SubdomainSuggestionService subdomainSuggestionService;
    private final JwtCookieProperties cookieProperties;

    @PostMapping("/company/register")
    public ResponseEntity<CompanyRegisterResponse> registerCompany(@Valid @RequestBody CompanyRegisterRequest request) {
        // K-21 phase 1: creates a PROVISIONING Company + verification token and emails the
        // link. The tenant schema and admin user do not exist yet — the resource is not
        // ready, so 202 Accepted (not 201 Created).
        CompanyRegisterResponse body = tenantProvisioningService.createPendingCompany(request);
        return ResponseEntity.accepted().body(body);
    }

    @PostMapping("/company/verify")
    public ResponseEntity<CompanyVerifyResponse> verifyCompany(@Valid @RequestBody CompanyVerifyRequest request) {
        // K-21 phase 2: consumes the token, runs CREATE SCHEMA + Flyway + admin user,
        // promotes the Company to ACTIVE. Synchronous (heavy) — see TenantProvisioningService.
        CompanyVerifyResponse body = tenantProvisioningService.verifyAndProvision(request.token());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/company/suggest-subdomain")
    public ResponseEntity<SubdomainSuggestionResponse> suggestSubdomain(
            @Valid @RequestBody SubdomainSuggestionRequest request) {
        return ResponseEntity.ok(subdomainSuggestionService.suggest(request.name()));
    }

    /**
     * Consumes an email-verification token (user lifecycle, optional policy). Public —
     * the tenant is resolved by {@code TenantFilter} from the subdomain-anchored link
     * host; the token itself is single-use and digest-stored.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<EmailVerificationResponse> verifyEmail(@Valid @RequestBody EmailVerifyRequest request) {
        userService.verifyEmail(request.token());
        return ResponseEntity.ok(new EmailVerificationResponse("E-posta adresiniz doğrulandı. Giriş yapabilirsiniz."));
    }

    /**
     * Starts the self-service password reset. ALWAYS 200 — unknown addresses and
     * mail failures are indistinguishable from success (no account enumeration).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<EmailVerificationResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userService.requestPasswordReset(request.email());
        return ResponseEntity.ok(new EmailVerificationResponse(
                "E-posta adresi kayıtlıysa şifre sıfırlama bağlantısı gönderildi."));
    }

    /**
     * Completes the self-service password reset: consumes the single-use token,
     * applies the new password and kills all of the user's sessions.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<EmailVerificationResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPasswordWithToken(request.token(), request.newPassword());
        return ResponseEntity.ok(new EmailVerificationResponse(
                "Şifreniz güncellendi. Yeni şifrenizle giriş yapabilirsiniz."));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse body = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieProperties.buildAccessTokenCookie(body.accessToken(), body.expiresIn()));
        response.addHeader(HttpHeaders.SET_COOKIE, cookieProperties.buildRefreshTokenCookie(body.refreshToken()));
        return ResponseEntity.ok(body);
    }

    /**
     * Rotates the refresh token (cookie or body) and mints a fresh access token (K-34).
     * Public (no access token required) — the tenant is resolved by {@code TenantFilter}
     * and the new access token is bound to it.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody(required = false) RefreshRequest body,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        String refreshToken = resolveRefreshToken(body, request);
        LoginResponse bodyOut = authService.refresh(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieProperties.buildAccessTokenCookie(bodyOut.accessToken(), bodyOut.expiresIn()));
        response.addHeader(HttpHeaders.SET_COOKIE, cookieProperties.buildRefreshTokenCookie(bodyOut.refreshToken()));
        return ResponseEntity.ok(bodyOut);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails principal,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        // [K-34] Per-session logout: consume this device's refresh token + blacklist the
        // current access token's jti (granular revoke). Other devices keep working — the
        // user-scoped tokenInvalidBefore is reserved for password change/reset/reuse.
        UUID userId = principal == null ? null : principal.getUserId();
        String jti = principal == null ? null : principal.getJti();
        String refreshToken = resolveRefreshToken(null, request);
        if (userId != null) {
            authService.logout(userId, jti, refreshToken);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieProperties.expireCookie(
                cookieProperties.effectiveCookieName(), "/"));
        response.addHeader(HttpHeaders.SET_COOKIE, cookieProperties.expireCookie(
                cookieProperties.effectiveRefreshCookieName(), cookieProperties.effectiveRefreshCookiePath()));
        return ResponseEntity.noContent().build();
    }

    /** Body takes precedence; falls back to the refresh-token cookie. */
    private String resolveRefreshToken(RefreshRequest body, HttpServletRequest request) {
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return body.refreshToken();
        }
        return cookieProperties.readRefreshCookie(request);
    }
}
