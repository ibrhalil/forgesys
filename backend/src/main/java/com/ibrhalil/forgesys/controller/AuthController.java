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
import com.ibrhalil.forgesys.dto.PlatformSwitchExchangeRequest;
import com.ibrhalil.forgesys.dto.PlatformSwitchExchangeResponse;
import com.ibrhalil.forgesys.dto.RefreshRequest;
import com.ibrhalil.forgesys.dto.ResetPasswordRequest;
import com.ibrhalil.forgesys.dto.SubdomainSuggestionRequest;
import com.ibrhalil.forgesys.dto.SubdomainSuggestionResponse;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.jwt.JwtCookieProperties;
import com.ibrhalil.forgesys.service.AuthService;
import com.ibrhalil.forgesys.service.PlatformSwitchService;
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
    private final PlatformSwitchService platformSwitchService;
    private final JwtCookieProperties cookieProperties;

    @PostMapping("/company/register")
    public ResponseEntity<CompanyRegisterResponse> registerCompany(@Valid @RequestBody CompanyRegisterRequest request) {
        // Phase 1 only: PROVISIONING Company + token email — tenant not ready yet,
        // so 202 Accepted, not 201 Created.
        CompanyRegisterResponse body = tenantProvisioningService.createPendingCompany(request);
        return ResponseEntity.accepted().body(body);
    }

    @PostMapping("/company/verify")
    public ResponseEntity<CompanyVerifyResponse> verifyCompany(@Valid @RequestBody CompanyVerifyRequest request) {
        // Phase 2: CREATE SCHEMA + Flyway + admin user; synchronous and heavy.
        CompanyVerifyResponse body = tenantProvisioningService.verifyAndProvision(request.token());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/company/suggest-subdomain")
    public ResponseEntity<SubdomainSuggestionResponse> suggestSubdomain(
            @Valid @RequestBody SubdomainSuggestionRequest request) {
        return ResponseEntity.ok(subdomainSuggestionService.suggest(request.name()));
    }

    /** Consumes a single-use email-verification token; tenant resolved from the subdomain-anchored link host. */
    @PostMapping("/verify-email")
    public ResponseEntity<EmailVerificationResponse> verifyEmail(@Valid @RequestBody EmailVerifyRequest request) {
        userService.verifyEmail(request.token());
        return ResponseEntity.ok(new EmailVerificationResponse("E-posta adresiniz doğrulandı. Giriş yapabilirsiniz."));
    }

    /** ALWAYS 200 — unknown addresses and mail failures are indistinguishable (no enumeration). */
    @PostMapping("/forgot-password")
    public ResponseEntity<EmailVerificationResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userService.requestPasswordReset(request.email());
        return ResponseEntity.ok(new EmailVerificationResponse(
                "E-posta adresi kayıtlıysa şifre sıfırlama bağlantısı gönderildi."));
    }

    /** Consumes the reset token, applies the new password, kills all of the user's sessions. */
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

    /** Rotates the refresh token (cookie or body) and mints a fresh access token (K-34); public. */
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

    /**
     * K-50 F6: exchanges a one-time platform switch code for a short-lived
     * impersonation JWT. Runs on the target tenant's subdomain host (TenantFilter
     * NORMAL flow) — the code's schema must match the resolved tenant. Sets the
     * tenant access cookie; NO refresh token is issued. Public (permitAll).
     */
    @PostMapping("/platform-switch")
    public ResponseEntity<PlatformSwitchExchangeResponse> platformSwitch(
            @Valid @RequestBody PlatformSwitchExchangeRequest request,
            HttpServletResponse response) {
        PlatformSwitchExchangeResponse body = platformSwitchService.exchange(request.code());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieProperties.buildAccessTokenCookie(body.accessToken(), body.expiresIn()));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails principal,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        // Per-session logout (K-34): consume this device's refresh + blacklist the jti.
        // tokenInvalidBefore stays reserved for password change/reset/reuse.
        UUID userId = principal == null ? null : principal.getUserId();
        String jti = principal == null ? null : principal.getJti();
        String refreshToken = resolveRefreshToken(null, request);
        if (principal != null && principal.isImpersonation()) {
            // K-50 F6: impersonation sessions have no refresh token — logout ends the
            // switch itself (jti blacklist + concurrency-guard clear + platform audit).
            UUID actorId = parseActorId(principal.getActUserId());
            if (actorId != null) {
                platformSwitchService.end(actorId, jti);
            }
        } else if (userId != null) {
            authService.logout(userId, jti, refreshToken);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieProperties.expireCookie(
                cookieProperties.effectiveCookieName(), "/"));
        response.addHeader(HttpHeaders.SET_COOKIE, cookieProperties.expireCookie(
                cookieProperties.effectiveRefreshCookieName(), cookieProperties.effectiveRefreshCookiePath()));
        return ResponseEntity.noContent().build();
    }

    /** Defensive parse of the act claim (K-50); null when absent or malformed. */
    private UUID parseActorId(String actUserId) {
        if (actUserId == null || actUserId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(actUserId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Body takes precedence; falls back to the refresh-token cookie. */
    private String resolveRefreshToken(RefreshRequest body, HttpServletRequest request) {
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return body.refreshToken();
        }
        return cookieProperties.readRefreshCookie(request);
    }
}
