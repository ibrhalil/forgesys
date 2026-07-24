package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.LoginRequest;
import com.ibrhalil.forgesys.dto.LoginResponse;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.exception.AuthException;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetailsService;
import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

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
 * <p>Deferred: refresh tokens, logout (Redis blacklist), token revocation
 * ({@code tokenInvalidBefore} check in the filter — [RISK-21] open), login-history write.
 * Note that a locked account's already-issued access token stays valid until it expires
 * (≤ TTL); immediate session kill arrives with [RISK-21].
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

    /**
     * Validates credentials and mints an access token. {@code noRollbackFor} is set so
     * the failed-attempt counter / lockout write (performed right before a
     * {@code badCredentials} throw) is committed rather than rolled back with the
     * exception — the lockout state must survive login failures.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(AuthException::badCredentials);
        UserAccount account = user.getUserAccount();
        if (account == null) {
            // Account-less user cannot authenticate; treat as bad credentials to keep
            // the uniform failure shape (no enumeration oracle).
            throw AuthException.badCredentials();
        }

        // [RISK-22] Checked before the password compare so a locked account leaks no
        // timing / attempt information. When the lock window has already expired the
        // counter is reset so the user gets a fresh attempt budget.
        if (account.getLockedUntil() != null) {
            if (account.getLockedUntil().isAfter(OffsetDateTime.now())) {
                throw AuthException.accountLocked();
            }
            account.setLockedUntil(null);
            account.setFailedLoginAttempts(0);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            registerFailedAttempt(user, account);
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
        String token = tokenProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), tenantSchema, authorities);
        long expiresIn = tokenProvider.getAccessTokenTtlMinutes() * 60;
        return new LoginResponse(token, "Bearer", expiresIn, user.getId(), user.getEmail(), authorities);
    }

    /**
     * [RISK-22] Records a failed login: increments the counter and, once the threshold
     * is reached, sets the temporary lockout window. The remaining attempt count is
     * never leaked — the caller still gets {@code auth_bad_credentials}.
     */
    private void registerFailedAttempt(User user, UserAccount account) {
        int attempts = account.getFailedLoginAttempts() + 1;
        account.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            account.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            log.warn("User {} locked after {} failed login attempts until {}",
                    user.getId(), attempts, account.getLockedUntil());
        }
        userRepository.save(user);
    }
}
