package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
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
 * Immediate session revocation for privilege changes (Faz 1): stamps
 * {@code UserAccount.tokenInvalidBefore} (kills outstanding access tokens, RISK-21) and
 * drops refresh tokens so the revoked permission is enforced on the next request, not at TTL.
 * rationale: docs/CODE_NOTES.md (backend/security → SessionRevocationService)
 */
@Slf4j
@Service
public class SessionRevocationService {

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final RoleRepository roleRepository;
    /** Max concurrent active sessions per user; {@code <=0} means unlimited (Faz 5). */
    private final int maxSessions;

    public SessionRevocationService(UserRepository userRepository,
                                    RefreshTokenStore refreshTokenStore,
                                    RoleRepository roleRepository,
                                    @Value("${forgesys.security.max-sessions:0}") int maxSessions) {
        this.userRepository = userRepository;
        this.refreshTokenStore = refreshTokenStore;
        this.roleRepository = roleRepository;
        this.maxSessions = maxSessions;
    }

    /** Revokes a single user's sessions. No-op when {@code userId} is null. */
    @Transactional
    public void revokeUser(UUID userId) {
        revokeUsers(userId == null ? List.<UUID>of() : List.of(userId));
    }

    /**
     * Stamps {@code tokenInvalidBefore} for one user WITHOUT touching refresh tokens —
     * sibling devices eat a single 401 + silent refresh and recover; only the targeted
     * (already-refresh-revoked) device is signed out.
     */
    @Transactional
    public void invalidateAccessTokens(UUID userId) {
        if (userId == null) {
            return;
        }
        userRepository.bulkSetTokenInvalidBefore(List.of(userId), OffsetDateTime.now());
    }

    /** Stamps {@code tokenInvalidBefore} + drops refresh tokens; nulls/duplicates collapsed, empty input no-op. */
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

    /** Revokes every user holding {@code roleId}, directly or via an active group. */
    @Transactional
    public void revokeRoleHolders(UUID roleId) {
        if (roleId == null) {
            return;
        }
        revokeUsers(userRepository.findUserIdsByRole(roleId));
    }

    /**
     * Holder ids resolved WITHOUT revoking — before the soft-delete ({@code @SQLRestriction}
     * would hide the role) and before the last-admin guard (a rejected delete leaves no revoke behind).
     */
    public List<UUID> resolveRoleHolderIds(UUID roleId) {
        if (roleId == null) {
            return List.of();
        }
        return userRepository.findUserIdsByRole(roleId);
    }

    /** Revokes every member of {@code groupId}. */
    @Transactional
    public void revokeGroupMembers(UUID groupId) {
        if (groupId == null) {
            return;
        }
        revokeUsers(userRepository.findUserIdsByGroup(groupId));
    }

    /** Revokes holders of any all-permissions role — fired on permission create/rename so they pick the new name up at next request, not at TTL. */
    @Transactional
    public void revokeAllPermissionsRoleHolders() {
        Set<UUID> userIds = new LinkedHashSet<>();
        for (Role role : roleRepository.findAllByAllPermissionsTrue()) {
            userIds.addAll(userRepository.findUserIdsByRole(role.getId()));
        }
        revokeUsers(userIds);
    }

    /**
     * Faz 5 session cap: evicts the oldest sessions ({@code lastSeen} first) when a login
     * pushes the user over {@code maxSessions} ({@code <=0} = unlimited). Rotation preserves
     * {@code sessionId}, so it never adds a session.
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
