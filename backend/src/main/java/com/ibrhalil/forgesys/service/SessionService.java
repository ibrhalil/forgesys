package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.ActiveSessionResponse;
import com.ibrhalil.forgesys.dto.AdminSessionResponse;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import com.ibrhalil.forgesys.security.refresh.ActiveSession;
import com.ibrhalil.forgesys.security.refresh.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Active-session management (K-28) via the refresh-token store, scoped to the request
 * tenant + owner user. Revoke semantics: dropping the refresh alone is not enough —
 * {@code tokenInvalidBefore} is also stamped so outstanding access tokens die now.
 * The stamp is USER-scoped (no per-session {@code jti} storage yet): sibling devices
 * briefly 401 then silent-refresh; the targeted device is fully signed out.
 * Rationale: docs/CODE_NOTES.md (backend/service → SessionService).
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    static final String ENTITY_TYPE = "Session";

    private final RefreshTokenStore refreshTokenStore;
    private final SessionRevocationService sessionRevocationService;
    private final AuditService auditService;

    /** Lists the caller's own sessions, flagging the current refresh cookie's session. */
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
    @AuditLog(action = "session_revoked", entityType = "Session", entityId = "#sessionId", entityName = "")
    public void revokeMySession(UUID userId, UUID sessionId) {
        if (!refreshTokenStore.revokeSession(userId, currentTenant(), sessionId)) {
            throw notFound();
        }
        // Kill outstanding access tokens now (user-scoped stamp: siblings 401 +
        // silent-refresh, the target device dies).
        sessionRevocationService.invalidateAccessTokens(userId);
    }

    /**
     * Whether the session behind {@code presentedRefreshToken} is {@code sessionId}
     * (used to clear the dead refresh cookie on self-revoke).
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

    /** Tenant-wide session view; each row carries its owner (userId + email). */
    @Transactional(readOnly = true)
    public List<AdminSessionResponse> listAllSessions() {
        return refreshTokenStore.listAllSessions(currentTenant()).stream()
                .map(s -> new AdminSessionResponse(s.sessionId(), s.userId(), s.email(),
                        nullIfBlank(s.userAgent()), nullIfBlank(s.ipAddress()), s.loginAt(), s.lastSeen()))
                .toList();
    }

    /** Admin ends a single session of another user. */
    @AuditLog(action = "session_revoked", entityType = "Session", entityId = "#sessionId", entityName = "")
    public void revokeUserSession(UUID targetUserId, UUID sessionId) {
        if (!refreshTokenStore.revokeSession(targetUserId, currentTenant(), sessionId)) {
            throw notFound();
        }
        // Kill outstanding access tokens now; sibling devices 401 + silent-refresh.
        sessionRevocationService.invalidateAccessTokens(targetUserId);
    }

    /** Admin ends every session of a user. */
    @AuditLog(action = "sessions_revoked_all", entityType = "Session", entityId = "#targetUserId", entityName = "")
    public void revokeAllUserSessions(UUID targetUserId) {
        refreshTokenStore.revokeAllForUser(targetUserId, currentTenant());
        sessionRevocationService.invalidateAccessTokens(targetUserId);
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
