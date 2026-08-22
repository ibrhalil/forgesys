package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.dto.CompanyRegisterResponse;
import com.ibrhalil.forgesys.dto.CompanyVerifyRequest;
import com.ibrhalil.forgesys.dto.CompanyVerifyResponse;
import com.ibrhalil.forgesys.dto.LoginRequest;
import com.ibrhalil.forgesys.dto.LoginResponse;
import com.ibrhalil.forgesys.dto.RefreshRequest;
import com.ibrhalil.forgesys.dto.SubdomainSuggestionRequest;
import com.ibrhalil.forgesys.dto.SubdomainSuggestionResponse;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.jwt.JwtCookieProperties;
import com.ibrhalil.forgesys.service.AuthService;
import com.ibrhalil.forgesys.service.SubdomainSuggestionService;
import com.ibrhalil.forgesys.service.TenantProvisioningService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TenantProvisioningService tenantProvisioningService;
    private final AuthService authService;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse body = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(body.accessToken(), body.expiresIn()));
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(body.refreshToken()));
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
        response.addHeader(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(bodyOut.accessToken(), bodyOut.expiresIn()));
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(bodyOut.refreshToken()));
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
        response.addHeader(HttpHeaders.SET_COOKIE, expireCookie(
                cookieProperties.effectiveCookieName(), "/"));
        response.addHeader(HttpHeaders.SET_COOKIE, expireCookie(
                cookieProperties.effectiveRefreshCookieName(), cookieProperties.effectiveRefreshCookiePath()));
        return ResponseEntity.noContent().build();
    }

    private String buildAccessTokenCookie(String token, long expiresInSeconds) {
        return ResponseCookie.from(cookieProperties.effectiveCookieName(), token)
                .httpOnly(true)
                .secure(cookieProperties.effectiveCookieSecure())
                .sameSite(cookieProperties.effectiveCookieSameSite())
                .path("/")
                .maxAge(expiresInSeconds)
                .build()
                .toString();
    }

    private String buildRefreshTokenCookie(String token) {
        return ResponseCookie.from(cookieProperties.effectiveRefreshCookieName(), token)
                .httpOnly(true)
                .secure(cookieProperties.effectiveRefreshCookieSecure())
                .sameSite(cookieProperties.effectiveCookieSameSite())
                .path(cookieProperties.effectiveRefreshCookiePath())
                .maxAge(cookieProperties.effectiveRefreshTokenTtlSeconds())
                .build()
                .toString();
    }

    private String expireCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieProperties.effectiveCookieSecure())
                .sameSite(cookieProperties.effectiveCookieSameSite())
                .path(path)
                .maxAge(0)
                .build()
                .toString();
    }

    /** Body takes precedence; falls back to the {@code sf_refresh_token} cookie. */
    private String resolveRefreshToken(RefreshRequest body, HttpServletRequest request) {
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return body.refreshToken();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> cookieProperties.effectiveRefreshCookieName().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
