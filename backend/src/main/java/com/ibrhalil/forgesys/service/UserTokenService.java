package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.UserAuthToken;
import com.ibrhalil.forgesys.entity.UserAuthTokenPurpose;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.UserAuthTokenRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-internal single-use auth tokens (user lifecycle): issue/consume for email
 * verification and password reset. Follows the [RISK-30]/[RISK-25] conventions:
 *
 * <ul>
 *   <li>Hash-at-rest — only the SHA-256 digest is persisted; the returned raw token
 *       goes straight into the mailed link.</li>
 *   <li>Re-issue supersedes — a new token of a purpose stamps the user's outstanding
 *       tokens of that purpose {@code usedAt}, so only the newest link works.</li>
 *   <li>Atomic claim — consumption is a conditional UPDATE; two concurrent consumers
 *       of the same link cannot both win.</li>
 * </ul>
 *
 * <p>Tenant-scoped: relies on the caller's {@code TenantContext} (request filter or a
 * set-and-restore window for out-of-request flows).
 */
@Service
@RequiredArgsConstructor
public class UserTokenService {

    private final UserAuthTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Value("${forgesys.security.email-verify-ttl-hours:24}")
    private long emailVerifyTtlHours;

    @Value("${forgesys.security.reset-token-ttl-minutes:30}")
    private long resetTokenTtlMinutes;

    /**
     * Issues a fresh token for the user + purpose and returns the RAW value (caller
     * builds the mailed link; the DB keeps only its digest).
     */
    @Transactional
    public String issue(UUID userId, UserAuthTokenPurpose purpose) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        tokenRepository.invalidateOutstanding(userId, purpose, now);

        String rawToken = UUID.randomUUID().toString();
        UserAuthToken token = new UserAuthToken();
        // Lazy proxy — the FK write needs no user hydration.
        token.setUser(userRepository.getReferenceById(userId));
        token.setPurpose(purpose);
        token.setTokenHash(TokenHasher.sha256Hex(rawToken));
        token.setExpiresAt(now.plus(ttlFor(purpose)));
        tokenRepository.save(token);
        return rawToken;
    }

    /**
     * Digest lookup WITHOUT consuming — returns the token entity whatever its
     * used/expired state (empty when unknown). Lets callers implement idempotent
     * flows: a used EMAIL_VERIFY token whose user is already verified can succeed
     * silently instead of surfacing {@code user_token_already_used} to a re-clicked
     * link. Validation/claim semantics live in {@link #consume}.
     */
    @Transactional(readOnly = true)
    public Optional<UserAuthToken> peek(String rawToken) {
        return tokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken));
    }

    /**
     * Validates and atomically consumes the presented raw token; returns the claimed
     * entity (carries {@code user} for the consuming flow). Error codes mirror the
     * tenant-signup semantics: {@code user_token_invalid} / {@code user_token_expired}
     * / {@code user_token_already_used}.
     */
    @Transactional
    public UserAuthToken consume(String rawToken, UserAuthTokenPurpose purpose) {
        String tokenHash = TokenHasher.sha256Hex(rawToken);
        UserAuthToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_TOKEN_INVALID));
        if (token.isUsed()) {
            throw new BusinessException(ErrorCode.USER_TOKEN_ALREADY_USED);
        }
        if (token.isExpired(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.USER_TOKEN_EXPIRED);
        }
        if (token.getPurpose() != purpose) {
            // Token exists but belongs to a different flow — do not let a password-reset
            // link consume an email-verification token (or vice versa).
            throw new BusinessException(ErrorCode.USER_TOKEN_INVALID);
        }

        OffsetDateTime claimedAt = OffsetDateTime.now(ZoneOffset.UTC);
        int claimedRows = tokenRepository.claimToken(tokenHash, claimedAt);
        if (claimedRows == 0) {
            throw new BusinessException(ErrorCode.USER_TOKEN_ALREADY_USED);
        }
        token.setUsedAt(claimedAt);
        return token;
    }

    /**
     * [RISK-30] Daily purge hook for {@code TokenPurgeJob} — deletes this tenant's
     * consumed/expired tokens older than the cutoff. Runs in its own transaction per
     * tenant schema (invoked with the job's set-and-restore TenantContext window).
     */
    @Transactional
    public int purgeStaleForCurrentTenant(OffsetDateTime cutoff) {
        return tokenRepository.purgeStale(cutoff);
    }

    /** TTL of a purpose — callers use it for the mailed copy ("valid for X"). */
    public Duration ttl(UserAuthTokenPurpose purpose) {
        return ttlFor(purpose);
    }

    private Duration ttlFor(UserAuthTokenPurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFY -> Duration.ofHours(emailVerifyTtlHours);
            case PASSWORD_RESET -> Duration.ofMinutes(resetTokenTtlMinutes);
        };
    }
}
