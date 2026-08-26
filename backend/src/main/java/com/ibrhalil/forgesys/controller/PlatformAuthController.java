package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.PlatformLoginRequest;
import com.ibrhalil.forgesys.dto.PlatformLoginResponse;
import com.ibrhalil.forgesys.dto.PlatformRefreshRequest;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.jwt.PlatformAuthProperties;
import com.ibrhalil.forgesys.service.PlatformAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** K-50 platform auth surface — cookie pair scoped to {@code /api/v1/platform}. */
@RestController
@RequestMapping("/api/v1/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;
    private final PlatformAuthProperties platformAuthProperties;

    @PostMapping("/login")
    public ResponseEntity<PlatformLoginResponse> login(@Valid @RequestBody PlatformLoginRequest request,
                                                       HttpServletResponse response) {
        PlatformLoginResponse body = platformAuthService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE,
                platformAuthProperties.buildAccessTokenCookie(body.accessToken(), body.expiresIn()));
        response.addHeader(HttpHeaders.SET_COOKIE,
                platformAuthProperties.buildRefreshTokenCookie(body.refreshToken()));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<PlatformLoginResponse> refresh(@RequestBody(required = false) PlatformRefreshRequest body,
                                                         HttpServletRequest request,
                                                         HttpServletResponse response) {
        String refreshToken = resolveRefreshToken(body, request);
        PlatformLoginResponse bodyOut = platformAuthService.refresh(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE,
                platformAuthProperties.buildAccessTokenCookie(bodyOut.accessToken(), bodyOut.expiresIn()));
        response.addHeader(HttpHeaders.SET_COOKIE,
                platformAuthProperties.buildRefreshTokenCookie(bodyOut.refreshToken()));
        return ResponseEntity.ok(bodyOut);
    }

    @PostMapping("/logout")
    @PreAuthorize("authentication.principal.scope == 'platform'")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails principal,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        UUID userId = principal == null ? null : principal.getUserId();
        String jti = principal == null ? null : principal.getJti();
        String refreshToken = resolveRefreshToken(null, request);
        if (userId != null) {
            platformAuthService.logout(userId, jti, refreshToken);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, platformAuthProperties.expireCookie(
                PlatformAuthProperties.ACCESS_COOKIE_NAME, "/"));
        response.addHeader(HttpHeaders.SET_COOKIE, platformAuthProperties.expireCookie(
                PlatformAuthProperties.REFRESH_COOKIE_NAME, platformAuthProperties.effectiveCookiePath()));
        return ResponseEntity.noContent().build();
    }

    /** Body takes precedence; falls back to the platform refresh-token cookie. */
    private String resolveRefreshToken(PlatformRefreshRequest body, HttpServletRequest request) {
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return body.refreshToken();
        }
        return platformAuthProperties.readRefreshCookie(request);
    }
}
