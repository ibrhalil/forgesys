package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.LoginRequest;
import com.ibrhalil.forgesys.dto.LoginResponse;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.exception.AuthException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.CustomUserDetailsService;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import com.ibrhalil.forgesys.security.TokenBlacklistService;
import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import com.ibrhalil.forgesys.security.refresh.IssuedRefresh;
import com.ibrhalil.forgesys.security.refresh.RefreshSession;
import com.ibrhalil.forgesys.security.refresh.RefreshTokenStore;
import com.ibrhalil.forgesys.security.refresh.RotationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication operations. {@link #login(LoginRequest)} validates credentials
 * against the tenant's user store (tenant resolved by {@code TenantFilter}) and
 * mints an RS256 access token.
 *
 * <p>Both an unknown email and a wrong password map to {@code auth_bad_credentials}
 * — the failure reason is never leaked (no user-enumeration oracle).
 *
 * <p><strong>Brute-force lockout ([RISK-22](../../../../docs/DECISIONS.md#risk-22)):</strong>
 * failed attempts are counted per user account; once {@link #MAX_FAILED_LOGIN_ATTEMPTS}
 * is reached the account is locked for {@link #LOCK_DURATION_MINUTES} minutes. A locked
 * account cannot log in (even with the correct password) until the window expires. The
 * remaining attempt count is never surfaced — the response stays
 * {@code auth_bad_credentials}. IP/tenant/email rate-limiting is deferred to Epic 2.6
 * (Redis).
 *
 * <p><strong>Lazy pepper migration (K-23):</strong> a successful login whose stored
 * hash is a legacy pepper-less BCrypt hash is silently rehashed to the peppered format
 * and persisted inline (the user is already loaded, no extra round-trip).
 *
 * <p><strong>Refresh + rotation + reuse detection (K-34):</strong> {@link #refresh(String)}
 * consumes an opaque refresh token (Redis-backed, hash-at-rest), mints a fresh access
 * token with freshly resolved authorities, and rotates the refresh token. Presenting an
 * already-consumed token is treated as reuse/compromise and revokes all of the user's
 * sessions (refresh tokens + {@code tokenInvalidBefore}). Per-session logout
 * ({@link #logout(UUID, String, String)}) blacklists the single access token's
 * {@code jti} and consumes the refresh token — other devices keep working.
 *
 * <p>Deferred: IP/tenant/email rate-limiting (Redis).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** [RISK-22] failed attempts before the account is temporarily locked. */
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    /** [RISK-22] lockout backoff window (minutes) applied once the threshold is reached. */
    private static final long LOCK_DURATION_MINUTES = 15;

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final LoginHistoryService loginHistoryService;
    private final RefreshTokenStore refreshTokenStore;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SessionRevocationService sessionRevocationService;

    /**
     * Validates credentials and mints an access token. {@code noRollbackFor} is set so
     * the failed-attempt counter / lockout write (performed right before a
     * {@code badCredentials} throw) is committed rather than rolled back with the
     * exception — the lockout state must survive login failures.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public LoginResponse login(LoginRequest request) {
        Optional<User> maybeUser = userRepository.findByEmail(request.email());
        if (maybeUser.isEmpty()) {
            // Timing defense: an unknown email still pays the bcrypt cost (encode is
            // discarded) so its response time matches a wrong-password attempt — no
            // user-enumeration oracle via timing.
            passwordEncoder.encode(request.password());
            loginHistoryService.record(null, request.email(), false, ErrorCode.AUTH_BAD_CREDENTIALS.code());
            throw AuthException.badCredentials();
        }
        User user = maybeUser.get();
        UserAccount account = user.getUserAccount();
        if (account == null) {
            // Account-less user cannot authenticate; treat as bad credentials to keep
            // the uniform failure shape (no enumeration oracle).
            loginHistoryService.record(user.getId(), user.getEmail(), false, ErrorCode.AUTH_BAD_CREDENTIALS.code());
            throw AuthException.badCredentials();
        }

        // [RISK-22] Checked before the password compare so a locked account leaks no
        // timing / attempt information. When the lock window has already expired the
        // counter is reset so the user gets a fresh attempt budget.
        if (account.getLockedUntil() != null) {
            if (account.getLockedUntil().isAfter(OffsetDateTime.now())) {
                loginHistoryService.record(user.getId(), user.getEmail(), false, ErrorCode.AUTH_ACCOUNT_LOCKED.code());
                throw AuthException.accountLocked();
            }
            account.setLockedUntil(null);
            account.setFailedLoginAttempts(0);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            registerFailedAttempt(user, account);
            loginHistoryService.record(user.getId(), user.getEmail(), false, ErrorCode.AUTH_BAD_CREDENTIALS.code());
            throw AuthException.badCredentials();
        }

        // Success: reset the attempt counter, stamp last login, and lazily migrate a
        // legacy pepper-less hash to the peppered format (K-23) inline.
        account.setFailedLoginAttempts(0);
        account.setLockedUntil(null);
        account.setLastLoginAt(OffsetDateTime.now());
        if (passwordEncoder.upgradeEncoding(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.password()));
            log.info("Rehashed legacy password to peppered format for user {}", user.getId());
        }
        userRepository.save(user);

        List<String> authorities = CustomUserDetailsService.resolveAuthorities(user).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String tenantSchema = TenantContext.getCurrentTenant().orElse(null);
        // K-28: capture the login device's IP + User-Agent (from the per-request
        // RequestContext, populated by RequestMetadataFilter) so the session list can
        // show "where you're logged in". The store keeps them with the refresh-token hash.
        String clientIp = null;
        String userAgent = null;
        if (com.ibrhalil.forgesys.web.RequestContext.current().isPresent()) {
            com.ibrhalil.forgesys.web.RequestMeta meta = com.ibrhalil.forgesys.web.RequestContext.current().get();
            clientIp = meta.clientIp();
            userAgent = meta.userAgent();
        }
        String token = tokenProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), tenantSchema, authorities);
        IssuedRefresh refresh = refreshTokenStore.issue(user.getId(), user.getEmail(), tenantSchema, clientIp, userAgent);
        // Faz 5: cap concurrent active sessions — if this login pushes the user over the
        // configured limit, the oldest sessions are evicted (login always succeeds).
        sessionRevocationService.enforceSessionLimit(user.getId());
        long expiresIn = tokenProvider.getAccessTokenTtlMinutes() * 60;
        loginHistoryService.record(user.getId(), user.getEmail(), true, null);
        return new LoginResponse(token, refresh.token(), "Bearer", expiresIn, user.getId(), user.getEmail(), authorities);
    }

    /**
     * Rotates a refresh token and mints a fresh access token (K-34). Authorities are
     * re-resolved from the DB (so permission changes + locked/disabled state take effect
     * at refresh) and the session tenant is bound to the request tenant (cross-tenant
     * replay rejected, mirroring [RISK-19]). {@code noRollbackFor} keeps the reuse
     * revocation writes committed even though reuse throws.
     *
     * <p>Reuse detection: an already-consumed (rotated) token revokes the user's refresh
     * tokens and stamps {@code tokenInvalidBefore} so outstanding access tokens die too,
     * then surfaces {@code auth_refresh_token_reuse}.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public LoginResponse refresh(String presentedRefreshToken) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            throw AuthException.refreshTokenInvalid();
        }
        String requestTenant = TenantContext.getCurrentTenant().orElse("public");
        RotationResult result = refreshTokenStore.rotate(presentedRefreshToken);
        return switch (result) {
            case RotationResult.Rotated rotated -> {
                RefreshSession session = rotated.issued().session();
                String sessionTenant = session.tenant() == null ? "public" : session.tenant();
                if (!sessionTenant.equals(requestTenant)) {
                    // Cross-tenant replay: drop the freshly rotated token and reject.
                    refreshTokenStore.revoke(rotated.issued().token());
                    log.debug("Refresh token tenant [{}] != request tenant [{}]; rejecting",
                            sessionTenant, requestTenant);
                    throw AuthException.refreshTokenInvalid();
                }
                CustomUserDetails principal;
                try {
                    principal = userDetailsService.loadUserByUsername(session.email());
                } catch (UsernameNotFoundException e) {
                    throw AuthException.refreshTokenInvalid();
                }
                if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
                    loginHistoryService.record(principal.getUserId(), principal.getEmail(),
                            false, ErrorCode.AUTH_REFRESH_TOKEN_INVALID.code());
                    throw AuthException.refreshTokenInvalid();
                }
                List<String> authorities = principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();
                String access = tokenProvider.generateAccessToken(
                        principal.getUserId().toString(), principal.getEmail(), sessionTenant, authorities);
                long expiresIn = tokenProvider.getAccessTokenTtlMinutes() * 60;
                loginHistoryService.record(principal.getUserId(), principal.getEmail(), true, null);
                yield new LoginResponse(access, rotated.issued().token(), "Bearer", expiresIn,
                        principal.getUserId(), principal.getEmail(), authorities);
            }
            case RotationResult.ReuseDetected reuse -> {
                invalidateAllTokens(reuse.userId());
                refreshTokenStore.revokeAllForUser(reuse.userId(), reuse.tenant());
                log.warn("Refresh token reuse for user {} tenant {}; revoked all sessions",
                        reuse.userId(), reuse.tenant());
                throw AuthException.refreshTokenReuse();
            }
            case RotationResult.Unknown ignored -> throw AuthException.refreshTokenInvalid();
        };
    }

    /**
     * Per-session logout (K-34): consumes the presented refresh token and blacklists the
     * current access token's {@code jti}. Only this session is killed — other devices
     * keep working. The user-scoped {@code tokenInvalidBefore} is NOT set here (that
     * remains the nuclear path for password change/reset/reuse).
     */
    public void logout(UUID userId, String jti, String presentedRefreshToken) {
        if (presentedRefreshToken != null && !presentedRefreshToken.isBlank()) {
            refreshTokenStore.revoke(presentedRefreshToken);
        }
        long ttl = tokenProvider.getAccessTokenTtlMinutes() * 60;
        tokenBlacklistService.blacklist(jti, ttl);
    }

    /**
     * Stamps {@code tokenInvalidBefore = now()} for the user (kills all outstanding
     * access tokens). Used on refresh-token reuse. Silent if the user/account is gone.
     */
    private void invalidateAllTokens(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            UserAccount account = user.getUserAccount();
            if (account != null) {
                account.setTokenInvalidBefore(OffsetDateTime.now());
                userRepository.save(user);
            }
        });
    }

    /**
     * [RISK-22 + Faz 1] Records a failed login: increments the counter and, once the threshold
     * is reached, sets the temporary lockout window. The remaining attempt count is never
     * leaked — the caller still gets {@code auth_bad_credentials}. On lock the user's
     * {@code tokenInvalidBefore} is also stamped so the account's outstanding access tokens
     * die immediately (not at TTL) — a locked account is treated as suspected compromise /
     * attack, so its live sessions are killed on the spot. The refresh path is already
     * blocked while locked ({@code accountNonLocked} re-check at refresh); refresh tokens
     * are therefore left in place so they work again once the lock window expires.
     */
    private void registerFailedAttempt(User user, UserAccount account) {
        int attempts = account.getFailedLoginAttempts() + 1;
        account.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            OffsetDateTime now = OffsetDateTime.now();
            account.setLockedUntil(now.plusMinutes(LOCK_DURATION_MINUTES));
            account.setTokenInvalidBefore(now);
            log.warn("User {} locked after {} failed login attempts until {}; sessions revoked",
                    user.getId(), attempts, account.getLockedUntil());
        }
        userRepository.save(user);
    }
}
