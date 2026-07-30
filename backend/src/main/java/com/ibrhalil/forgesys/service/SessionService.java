package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.ActiveSessionResponse;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.security.refresh.ActiveSession;
import com.ibrhalil.forgesys.security.refresh.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Active-session management (K-28). Reads and revokes refresh-token sessions via
 * {@link RefreshTokenStore}. Sessions are scoped to the request tenant
 * ({@link TenantContext}) and an owner user id; the self endpoints self-scope to the
 * authenticated principal, the admin endpoints take an explicit user id.
 *
 * <p>Revoke semantics: ending a session drops its refresh token so it can no longer
 * rotate (the device is signed out at the next access-token expiry). The matching
 * access token is NOT blacklisted — its short-lived {@code jti} is not stored
 * per-session — so a revoked device keeps its access token only until TTL (minutes).
 * Nuclear revoke ({@link #revokeAllUserSessions}) delegates to the store's
 * {@code revokeAllForUser} (used by password change / reuse elsewhere).
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    static final String ENTITY_TYPE = "Session";

    private final RefreshTokenStore refreshTokenStore;
    private final AuditService auditService;

    /**
     * Lists the caller's own active sessions, flagging the one behind
     * {@code currentRefreshToken} (the httpOnly cookie) as current.
     */
    @Transactional(readOnly = true)
    public List<ActiveSessionResponse> listMySessions(UUID userId, String currentRefreshToken) {
        String tenant = currentTenant();
        UUID currentSessionId = currentRefreshToken == null || currentRefreshToken.isBlank()
                ? null
                : refreshTokenStore.activeSessionFor(currentRefreshToken)
                        .map(ActiveSession::sessionId).orElse(null);
        return refreshTokenStore.listSessions(userId, tenant).stream()
                .map(s -> toResponse(s, s.sessionId().equals(currentSessionId)))
                .toList();
    }

    /** Ends one of the caller's own sessions. */
    public void revokeMySession(UUID userId, UUID sessionId) {
        if (!refreshTokenStore.revokeSession(userId, currentTenant(), sessionId)) {
            throw notFound();
        }
        auditService.record("session_revoked", ENTITY_TYPE, sessionId, null);
    }

    /**
     * Whether the session behind {@code presentedRefreshToken} is {@code sessionId}.
     * Used by the self-revoke path to clear the dead refresh cookie when the caller
     * ended their own current device.
     */
    public boolean isCurrentSession(String presentedRefreshToken, UUID sessionId) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            return false;
        }
        return refreshTokenStore.activeSessionFor(presentedRefreshToken)
                .map(ActiveSession::sessionId)
                .map(sessionId::equals)
                .orElse(false);
    }

    /** Admin view of another user's active sessions (current is always false). */
    @Transactional(readOnly = true)
    public List<ActiveSessionResponse> listUserSessions(UUID targetUserId) {
        return refreshTokenStore.listSessions(targetUserId, currentTenant()).stream()
                .map(s -> toResponse(s, false))
                .toList();
    }

    /** Admin ends a single session of another user. */
    public void revokeUserSession(UUID targetUserId, UUID sessionId) {
        if (!refreshTokenStore.revokeSession(targetUserId, currentTenant(), sessionId)) {
            throw notFound();
        }
        auditService.record("session_revoked", ENTITY_TYPE, sessionId, null);
    }

    /** Admin ends every session of a user. */
    public void revokeAllUserSessions(UUID targetUserId) {
        refreshTokenStore.revokeAllForUser(targetUserId, currentTenant());
        auditService.record("sessions_revoked_all", ENTITY_TYPE, targetUserId, null);
    }

    private static ActiveSessionResponse toResponse(ActiveSession session, boolean current) {
        return new ActiveSessionResponse(
                session.sessionId(),
                nullIfBlank(session.userAgent()),
                nullIfBlank(session.ipAddress()),
                session.loginAt(),
                session.lastSeen(),
                current);
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String currentTenant() {
        return TenantContext.getCurrentTenant().orElse(null);
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.SESSION_NOT_FOUND);
    }
}
