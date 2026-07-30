package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.refresh.ActiveSession;
import com.ibrhalil.forgesys.security.refresh.RefreshTokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Immediate session revocation for one or more users (Faz 1 — privilege-change revoke).
 *
 * <p>Authorities are embedded in the access token at issue time, so a role / permission /
 * group mutation otherwise keeps the affected users' tokens authoritative until the next
 * access-token issue (login / refresh) — up to the token TTL. To close that
 * privilege-retention window this service stamps {@code UserAccount.tokenInvalidBefore}
 * for every affected user (kills all outstanding access tokens — user-scoped revoke,
 * [RISK-21]) and drops their refresh tokens ({@link RefreshTokenStore#revokeAllForUser})
 * so a stolen refresh cannot mint a fresh access token whose {@code iat} post-dates the
 * revoke. The result: a revoked permission is enforced on the very next request, not at
 * TTL.
 *
 * <p>Caller responsibilities: resolve <em>who is affected</em> ({@link #revokeRoleHolders}
 * / {@link #revokeGroupMembers}) or pass explicit ids. The bulk access-token stamp is a
 * single conditional UPDATE (see {@code UserRepository.bulkSetTokenInvalidBefore}); the
 * refresh revoke is per-user (Redis) since the store is keyed per user.
 */
@Slf4j
@Service
public class SessionRevocationService {

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    /** Max concurrent active sessions per user; {@code <=0} means unlimited (Faz 5). */
    private final int maxSessions;

    public SessionRevocationService(UserRepository userRepository,
                                    RefreshTokenStore refreshTokenStore,
                                    @Value("${forgesys.security.max-sessions:0}") int maxSessions) {
        this.userRepository = userRepository;
        this.refreshTokenStore = refreshTokenStore;
        this.maxSessions = maxSessions;
    }

    /** Revokes a single user's sessions. No-op when {@code userId} is null. */
    @Transactional
    public void revokeUser(UUID userId) {
        revokeUsers(userId == null ? List.<UUID>of() : List.of(userId));
    }

    /**
     * Revokes sessions for every user in {@code userIds}: stamps
     * {@code tokenInvalidBefore} and drops refresh tokens. Nulls and duplicates are
     * collapsed; empty / blank input is a no-op. Refresh revoke only fires when a tenant
     * is bound (the refresh store is tenant-scoped).
     */
    @Transactional
    public void revokeUsers(Collection<UUID> userIds) {
        if (userIds == null) {
            return;
        }
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID id : userIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return;
        }
        int updated = userRepository.bulkSetTokenInvalidBefore(distinct, OffsetDateTime.now());
        Optional<String> tenant = TenantContext.getCurrentTenant();
        if (tenant.isPresent()) {
            for (UUID id : distinct) {
                refreshTokenStore.revokeAllForUser(id, tenant.get());
            }
        }
        log.debug("Revoked sessions for {} user(s); {} account row(s) stamped tokenInvalidBefore",
                distinct.size(), updated);
    }

    /**
     * Revokes every user holding {@code roleId}, directly or via an active group. Use on
     * role permission/name changes and role deletion so the permission delta is enforced
     * immediately for all bearers.
     */
    @Transactional
    public void revokeRoleHolders(UUID roleId) {
        if (roleId == null) {
            return;
        }
        revokeUsers(userRepository.findUserIdsByRole(roleId));
    }

    /**
     * Revokes every member of {@code groupId}. Use on group role / membership / active
     * toggle changes and group deletion so the group's role delta is enforced immediately
     * for all members.
     */
    @Transactional
    public void revokeGroupMembers(UUID groupId) {
        if (groupId == null) {
            return;
        }
        revokeUsers(userRepository.findUserIdsByGroup(groupId));
    }

    /**
     * Faz 5 concurrent-session limit. Called after a new session is issued
     * ({@code AuthService.login}): when the user now holds more than {@code maxSessions}
     * active sessions the oldest ones are evicted (oldest {@code lastSeen} first) so a
     * login always succeeds while the active-session count stays at/below the cap. A cap
     * of {@code <=0} is unlimited (no-op). Implemented with the existing K-28 session
     * primitives ({@link RefreshTokenStore#listSessions} / {@link RefreshTokenStore#revokeSession})
     * so it needs no store-contract change; the evicted device's short-lived access token
     * expires at its TTL (its {@code jti} is not stored per-session — same as an admin
     * remote-revoke). Rotation never triggers this: it preserves the {@code sessionId},
     * so it does not add a session.
     */
    public void enforceSessionLimit(UUID userId) {
        if (userId == null || maxSessions <= 0) {
            return;
        }
        String tenant = TenantContext.getCurrentTenant().orElse(null);
        List<ActiveSession> sessions = refreshTokenStore.listSessions(userId, tenant);
        int excess = sessions.size() - maxSessions;
        if (excess <= 0) {
            return;
        }
        // listSessions returns newest activity first; evict the OLDEST tail (highest index).
        for (int i = sessions.size() - 1; excess > 0; i--, excess--) {
            UUID sessionId = sessions.get(i).sessionId();
            refreshTokenStore.revokeSession(userId, tenant, sessionId);
        }
        log.debug("Enforced max-sessions={} for user {} tenant {}; evicted {} oldest session(s)",
                maxSessions, userId, tenant, sessions.size() - maxSessions);
    }
}
