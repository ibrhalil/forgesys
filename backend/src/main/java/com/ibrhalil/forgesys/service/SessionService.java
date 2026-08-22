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
 * Active-session management (K-28). Reads and revokes refresh-token sessions via
 * {@link RefreshTokenStore}. Sessions are scoped to the request tenant
 * ({@link TenantContext}) and an owner user id; the self endpoints self-scope to the
 * authenticated principal, the admin endpoints take an explicit user id.
 *
 * <p>Revoke semantics: ending a session drops its refresh token <em>and</em> stamps
 * {@code UserAccount.tokenInvalidBefore} (via {@link SessionRevocationService}) so the
 * affected user's outstanding access tokens die immediately — the device is signed out on
 * its next request, not at access-token TTL. For a single-session revoke the stamp is
 * user-scoped (it is the only immediate lever available without per-session {@code jti}
 * storage), so other devices of the same user briefly 401 then recover via their
 * still-valid refresh token; the targeted device, whose refresh was dropped, is fully
 * signed out. {@link #revokeAllUserSessions} additionally drops every refresh token.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    static final String ENTITY_TYPE = "Session";

    private final RefreshTokenStore refreshTokenStore;
    private final SessionRevocationService sessionRevocationService;
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
    @AuditLog(action = "session_revoked", entityType = "Session", entityId = "#sessionId", entityName = "")
    public void revokeMySession(UUID userId, UUID sessionId) {
        if (!refreshTokenStore.revokeSession(userId, currentTenant(), sessionId)) {
            throw notFound();
        }
        // Kill the user's outstanding access tokens now (not at TTL) so the device is
        // signed out on its next request. Revoking the current device logs it out
        // immediately; revoking another own device briefly blips the current one (401 +
        // silent refresh) and recovers.
        sessionRevocationService.invalidateAccessTokens(userId);
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

    /**
     * Admin tenant-wide view: every active session across all users of the request
     * tenant (the "all sessions" table). Each row carries its owner (userId + email) so
     * the admin can see who is signed in where.
     */
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
        // Kill the user's outstanding access tokens now (not at TTL) so the device is
        // signed out on its next request. Sibling devices briefly 401 + silent-refresh.
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
