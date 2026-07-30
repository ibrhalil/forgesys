package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.ActiveSessionResponse;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.service.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Active-session management (K-28). Lists a user's currently usable refresh-token
 * sessions (one per device) and ends individual sessions or all of them.
 *
 * <p>Split by scope:
 * <ul>
 *   <li>Self — {@code /api/v1/users/me/sessions}: any authenticated user manages their
 *       own sessions. The literal {@code me} segment takes precedence over the
 *       {@code /{id}} variable (mirrors {@link UserProfileController}). The current
 *       device is flagged via the {@code sf_refresh_token} cookie.</li>
 *   <li>Admin — {@code /api/v1/users/{id}/sessions}: a holder of
 *       {@code iam:user:write} views or ends another user's sessions (remote revoke).</li>
 * </ul>
 *
 * <p>Ending a session drops its refresh token; the device's outstanding access token
 * expires at its TTL (its {@code jti} is not stored per-session). See
 * {@link SessionService}.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class SessionController {

    /** The httpOnly refresh-token cookie used to identify the caller's current device. */
    static final String REFRESH_COOKIE = "sf_refresh_token";

    private final SessionService sessionService;

    /* ── self ── */

    @GetMapping("/me/sessions")
    public ResponseEntity<List<ActiveSessionResponse>> mySessions(
            @AuthenticationPrincipal CustomUserDetails principal,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return ResponseEntity.ok(sessionService.listMySessions(principal.getUserId(), refreshToken));
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    public ResponseEntity<Void> revokeMySession(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID sessionId,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        sessionService.revokeMySession(principal.getUserId(), sessionId);
        // If the caller ended their own current device, clear the refresh cookie so the
        // browser stops sending a now-dead token.
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
            Cookie cookie = new Cookie(REFRESH_COOKIE, "");
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);
        }
    }
}
