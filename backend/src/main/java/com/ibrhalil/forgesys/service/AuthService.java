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
 * Authentication: login (credentials → RS256 access + opaque refresh), refresh
 * rotation with reuse detection (K-34), per-session logout. Unknown email and wrong
 * password are indistinguishable ({@code auth_bad_credentials} — no enumeration
 * oracle). Brute-force lockout ([RISK-22]) and lazy pepper migration (K-23) apply on
 * login. Rationale: docs/CODE_NOTES.md (backend/service → AuthService).
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
     * Validates credentials and mints an access token. {@code noRollbackFor} keeps the
     * failed-attempt counter / lockout write committed despite the badCredentials throw.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public LoginResponse login(LoginRequest request) {
        Optional<User> maybeUser = userRepository.findByEmail(request.email());
        if (maybeUser.isEmpty()) {
            // Timing defense: unknown email still pays the bcrypt cost — no timing oracle.
            passwordEncoder.encode(request.password());
            loginHistoryService.record(null, request.email(), false, ErrorCode.AUTH_BAD_CREDENTIALS.code());
            throw AuthException.badCredentials();
        }
        User user = maybeUser.get();
        UserAccount account = user.getUserAccount();
        if (account == null) {
            // Uniform failure shape — no enumeration oracle.
            loginHistoryService.record(user.getId(), user.getEmail(), false, ErrorCode.AUTH_BAD_CREDENTIALS.code());
            throw AuthException.badCredentials();
        }

        // [RISK-22] Lock check BEFORE the password compare (no timing/attempt leak);
        // an expired lock resets the attempt budget.
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

        // Disabled check AFTER the password compare — unknown-email vs disabled-account
        // is not probeable without valid credentials (refresh re-checks, K-34).
        if (!account.isEnabled()) {
            loginHistoryService.record(user.getId(), user.getEmail(), false, ErrorCode.AUTH_ACCOUNT_DISABLED.code());
            throw AuthException.accountDisabled();
        }

        // Reset the counter, stamp last login, lazily migrate a legacy hash (K-23).
        account.setFailedLoginAttempts(0);
        account.setLockedUntil(null);
        account.setLastLoginAt(OffsetDateTime.now());
        if (passwordEncoder.upgradeEncoding(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.password()));
            log.info("Rehashed legacy password to peppered format for user {}", user.getId());
        }
        userRepository.save(user);

        List<String> authorities = userDetailsService.resolveAuthorities(user.getId()).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String tenantSchema = TenantContext.getCurrentTenant().orElse(null);
        // K-28: device IP + User-Agent so the session list can show "where you're logged in".
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
        // Session cap: evict the oldest over the limit — login always succeeds.
        sessionRevocationService.enforceSessionLimit(user.getId());
        long expiresIn = tokenProvider.getAccessTokenTtlMinutes() * 60;
        loginHistoryService.record(user.getId(), user.getEmail(), true, null);
        return new LoginResponse(token, refresh.token(), "Bearer", expiresIn, user.getId(), user.getEmail(), authorities);
    }

    /**
     * Rotates a refresh token and mints a fresh access token (K-34): authorities
     * re-resolved from DB, session tenant bound to the request tenant ([RISK-19]).
     * Reuse of a consumed token revokes all sessions ({@code auth_refresh_token_reuse});
     * {@code noRollbackFor} keeps that revoke committed.
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
     * Per-session logout (K-34): consumes this refresh + blacklists the access
     * {@code jti} — other devices keep working ({@code tokenInvalidBefore} stays
     * reserved for password change/reset/reuse).
     */
    public void logout(UUID userId, String jti, String presentedRefreshToken) {
        if (presentedRefreshToken != null && !presentedRefreshToken.isBlank()) {
            refreshTokenStore.revoke(presentedRefreshToken);
        }
        long ttl = tokenProvider.getAccessTokenTtlMinutes() * 60;
        tokenBlacklistService.blacklist(jti, ttl);
    }

    /** Stamps {@code tokenInvalidBefore} for the user; silent if the user/account is gone. */
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
     * [RISK-22 + Faz 1] Counts the failure and, at the threshold, locks + stamps
     * {@code tokenInvalidBefore} — a locked account is treated as suspected compromise,
     * so live access tokens die immediately. Refresh tokens stay (refresh re-checks the
     * lock; they work again once unlocked). Attempt count is never leaked.
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
