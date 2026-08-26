package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.ActiveSessionResponse;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.jwt.JwtCookieProperties;
import com.ibrhalil.forgesys.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Active-session management (K-28): self ({@code /users/me/sessions}, any
 * authenticated user; current device flagged via the refresh cookie) and admin
 * ({@code /users/{id}/sessions}, remote revoke). Ending a session drops its
 * refresh token and stamps {@code tokenInvalidBefore} so the device's access
 * token dies on its next request rather than at TTL.
 * rationale: docs/CODE_NOTES.md (backend/web → UserSessionController)
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserSessionController {

    private final SessionService sessionService;
    private final JwtCookieProperties cookieProperties;

    /* ── self ── */

    @GetMapping("/me/sessions")
    public ResponseEntity<List<ActiveSessionResponse>> mySessions(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(sessionService.listMySessions(principal.getUserId(),
                cookieProperties.readRefreshCookie(request)));
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    public ResponseEntity<Void> revokeMySession(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID sessionId,
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = cookieProperties.readRefreshCookie(request);
        sessionService.revokeMySession(principal.getUserId(), sessionId);
        // Clear the refresh cookie if the caller ended their own current device.
        expireIfCurrent(response, refreshToken, sessionId);
        return ResponseEntity.noContent().build();
    }

    /* ── admin ── */

    @GetMapping("/{id}/sessions")
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<List<ActiveSessionResponse>> userSessions(@PathVariable UUID id) {
        return ResponseEntity.ok(sessionService.listUserSessions(id));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<Void> revokeUserSession(@PathVariable UUID id, @PathVariable UUID sessionId) {
        sessionService.revokeUserSession(id, sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/sessions")
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<Void> revokeAllUserSessions(@PathVariable UUID id) {
        sessionService.revokeAllUserSessions(id);
        return ResponseEntity.noContent().build();
    }

    private void expireIfCurrent(HttpServletResponse response, String refreshToken, UUID sessionId) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        boolean currentEnded = sessionService.isCurrentSession(refreshToken, sessionId);
        if (currentEnded) {
            // Clear BOTH cookies — instant logout, not just the (now-dead) refresh.
            response.addHeader(HttpHeaders.SET_COOKIE, cookieProperties.expireCookie(
                    cookieProperties.effectiveCookieName(), "/"));
            response.addHeader(HttpHeaders.SET_COOKIE, cookieProperties.expireCookie(
                    cookieProperties.effectiveRefreshCookieName(), cookieProperties.effectiveRefreshCookiePath()));
        }
    }
}
