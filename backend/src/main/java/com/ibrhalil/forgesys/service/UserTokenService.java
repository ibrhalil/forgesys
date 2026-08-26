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
 * Tenant-internal single-use lifecycle tokens (email verify + password reset).
 * Conventions ([RISK-30]/[RISK-25]): SHA-256 hash-at-rest (raw goes only into the
 * mailed link); re-issue supersedes the user's outstanding tokens of the purpose;
 * consumption is an atomic claim. Tenant-scoped via the caller's {@code TenantContext}.
 * Rationale: docs/CODE_NOTES.md (backend/service → UserTokenService).
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

    /** Issues a fresh token and returns the RAW value (the DB keeps only its digest). */
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

    /** Digest lookup WITHOUT consuming (any used/expired state) — idempotency probe base. */
    @Transactional(readOnly = true)
    public Optional<UserAuthToken> peek(String rawToken) {
        return tokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken));
    }

    /**
     * Validates and atomically consumes the raw token (carries {@code user} for the
     * consuming flow). Errors: {@code user_token_invalid} / {@code user_token_expired}
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
            // A token of another flow must not consume this one.
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

    /** [RISK-30] TokenPurgeJob hook — deletes consumed/expired tokens older than the cutoff. */
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
