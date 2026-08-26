package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.config.PlatformPermissionCatalog;
import com.ibrhalil.forgesys.dto.PlatformLoginRequest;
import com.ibrhalil.forgesys.dto.PlatformLoginResponse;
import com.ibrhalil.forgesys.entity.PlatformUser;
import com.ibrhalil.forgesys.entity.PlatformUserType;
import com.ibrhalil.forgesys.exception.AuthException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.security.TokenBlacklistService;
import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import com.ibrhalil.forgesys.security.refresh.RefreshSession;
import com.ibrhalil.forgesys.security.refresh.RefreshTokenStore;
import com.ibrhalil.forgesys.security.refresh.RotationResult;
import com.ibrhalil.forgesys.web.RequestContext;
import com.ibrhalil.forgesys.web.RequestMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * K-50 platform-identity auth: the AuthService flow mirrored for HUMAN superadmins —
 * peppered password login with the same lockout policy (RISK-22), Redis refresh
 * rotation + reuse detection (K-34, tenant marker {@code "platform"}) and
 * per-session logout. Audit goes to {@code t_platform_audit_logs} (never the tenant
 * {@code @AuditLog} AOP). SERVICE identities have no password and are rejected here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAuthService {

    /** [RISK-22] same brute-force policy as tenant login. */
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;

    /** Refresh-store tenant marker — distinct from every {@code tenant_*} schema name. */
    public static final String PLATFORM_TENANT_MARKER = "platform";

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final PlatformUserRepository platformUserRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklistService tokenBlacklistService;
    private final PlatformAuditService platformAuditService;

    @Transactional(noRollbackFor = AuthException.class)
    public PlatformLoginResponse login(PlatformLoginRequest request) {
        Optional<PlatformUser> maybeUser = platformUserRepository.findByEmail(request.email());
        if (maybeUser.isEmpty()) {
            passwordEncoder.encode(request.password());
            auditLogin(null, false, ErrorCode.AUTH_BAD_CREDENTIALS.code());
            throw AuthException.badCredentials();
        }
        PlatformUser user = maybeUser.get();
        if (user.getUserType() != PlatformUserType.HUMAN || user.getPasswordHash() == null) {
            // SERVICE identities authenticate via X-API-Key only — no enumeration oracle.
            passwordEncoder.encode(request.password());
            auditLogin(user.getId(), false, ErrorCode.AUTH_BAD_CREDENTIALS.code());
            throw AuthException.badCredentials();
        }

        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(OffsetDateTime.now())) {
                auditLogin(user.getId(), false, ErrorCode.AUTH_ACCOUNT_LOCKED.code());
                throw AuthException.accountLocked();
            }
            user.setLockedUntil(null);
            user.setFailedAttempts(0);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            auditLogin(user.getId(), false, ErrorCode.AUTH_BAD_CREDENTIALS.code());
            throw AuthException.badCredentials();
        }

        if (!user.isEnabled()) {
            auditLogin(user.getId(), false, ErrorCode.AUTH_ACCOUNT_DISABLED.code());
            throw AuthException.accountDisabled();
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        platformUserRepository.save(user);

        List<String> authorities = PlatformPermissionCatalog.ALL_NAMES;
        String token = tokenProvider.generatePlatformAccessToken(
                user.getId().toString(), user.getEmail(), authorities);
        IssuedTokens refresh = issueRefresh(user);
        auditLogin(user.getId(), true, null);
        long expiresIn = tokenProvider.getAccessTokenTtlMinutes() * 60;
        return new PlatformLoginResponse(token, refresh.token(), "Bearer", expiresIn,
                user.getId(), user.getEmail(), user.getDisplayName(), authorities);
    }

    /**
     * Rotates a platform refresh token (K-34 mirror): session tenant must be the
     * platform marker; account state re-checked from DB. Reuse revokes everything.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public PlatformLoginResponse refresh(String presentedRefreshToken) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            throw AuthException.refreshTokenInvalid();
        }
        RotationResult result = refreshTokenStore.rotate(presentedRefreshToken);
        return switch (result) {
            case RotationResult.Rotated rotated -> {
                RefreshSession session = rotated.issued().session();
                if (!PLATFORM_TENANT_MARKER.equals(session.tenant())) {
                    refreshTokenStore.revoke(rotated.issued().token());
                    throw AuthException.refreshTokenInvalid();
                }
                PlatformUser user = platformUserRepository.findById(session.userId())
                        .orElseThrow(AuthException::refreshTokenInvalid);
                if (user.getUserType() != PlatformUserType.HUMAN || !user.isEnabled()
                        || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now()))) {
                    throw AuthException.refreshTokenInvalid();
                }
                List<String> authorities = PlatformPermissionCatalog.ALL_NAMES;
                String access = tokenProvider.generatePlatformAccessToken(
                        user.getId().toString(), user.getEmail(), authorities);
                long expiresIn = tokenProvider.getAccessTokenTtlMinutes() * 60;
                yield new PlatformLoginResponse(access, rotated.issued().token(), "Bearer", expiresIn,
                        user.getId(), user.getEmail(), user.getDisplayName(), authorities);
            }
            case RotationResult.ReuseDetected reuse -> {
                platformUserRepository.findById(reuse.userId()).ifPresent(user -> {
                    user.setTokenInvalidBefore(OffsetDateTime.now());
                    platformUserRepository.save(user);
                });
                refreshTokenStore.revokeAllForUser(reuse.userId(), PLATFORM_TENANT_MARKER);
                log.warn("Platform refresh token reuse for user {}; revoked all sessions", reuse.userId());
                throw AuthException.refreshTokenReuse();
            }
            case RotationResult.Unknown ignored -> throw AuthException.refreshTokenInvalid();
        };
    }

    /** Per-session logout — same contract as the tenant flow (refresh + jti blacklist). */
    public void logout(UUID userId, String jti, String presentedRefreshToken) {
        if (presentedRefreshToken != null && !presentedRefreshToken.isBlank()) {
            refreshTokenStore.revoke(presentedRefreshToken);
        }
        long ttl = tokenProvider.getAccessTokenTtlMinutes() * 60;
        tokenBlacklistService.blacklist(jti, ttl);
    }

    private record IssuedTokens(String token) {
    }

    private IssuedTokens issueRefresh(PlatformUser user) {
        String clientIp = RequestContext.current().map(RequestMeta::clientIp).orElse(null);
        String userAgent = RequestContext.current().map(RequestMeta::userAgent).orElse(null);
        return new IssuedTokens(
                refreshTokenStore.issue(user.getId(), user.getEmail(), PLATFORM_TENANT_MARKER, clientIp, userAgent).token());
    }

    private void auditLogin(UUID userId, boolean success, String reason) {
        platformAuditService.record(userId,
                success || userId != null ? PlatformAuditService.ACTOR_HUMAN : PlatformAuditService.ACTOR_SYSTEM,
                success ? PlatformAuditService.ACTION_LOGIN_SUCCESS : PlatformAuditService.ACTION_LOGIN_FAILED,
                null, null, reason);
    }

    /** [RISK-22] same policy as the tenant flow: threshold → lock + tokenInvalidBefore. */
    private void registerFailedAttempt(PlatformUser user) {
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            OffsetDateTime now = OffsetDateTime.now();
            user.setLockedUntil(now.plusMinutes(LOCK_DURATION_MINUTES));
            user.setTokenInvalidBefore(now);
            log.warn("Platform user {} locked after {} failed login attempts until {}",
                    user.getId(), attempts, user.getLockedUntil());
        }
        platformUserRepository.save(user);
    }
}
