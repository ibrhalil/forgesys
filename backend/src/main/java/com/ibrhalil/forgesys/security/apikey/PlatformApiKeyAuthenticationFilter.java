package com.ibrhalil.forgesys.security.apikey;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.PlatformApiKey;
import com.ibrhalil.forgesys.entity.PlatformUser;
import com.ibrhalil.forgesys.entity.PlatformUserType;
import com.ibrhalil.forgesys.exception.ApiErrorFactory;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.PlatformApiKeyRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.TokenHasher;
import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import com.ibrhalil.forgesys.service.PlatformAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * K-50 F5 stateless service-account authentication via {@code X-API-Key}
 * {@code <prefix>_<secret>}: prefix lookup → SHA-256 hash compare (TokenHasher)
 * → key/account state checks → tenant-less {@code scope=platform} principal with
 * the key's scopes as authorities. Runs BEFORE {@code JwtAuthenticationFilter};
 * every failure clears the context → uniform 401 {@code platform_api_key_invalid}.
 * rationale: docs/CODE_NOTES.md (backend/security → PlatformApiKeyAuthenticationFilter)
 */
@Component
public class PlatformApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    private static final String PLATFORM_AUTH_PREFIX = "/api/v1/platform/auth/";

    private static final Logger log = LoggerFactory.getLogger(PlatformApiKeyAuthenticationFilter.class);

    private final PlatformApiKeyRepository platformApiKeyRepository;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public PlatformApiKeyAuthenticationFilter(PlatformApiKeyRepository platformApiKeyRepository,
                                              PlatformAuditService platformAuditService,
                                              ObjectMapper objectMapper) {
        this.platformApiKeyRepository = platformApiKeyRepository;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    /** Matches only requests carrying the header; NEVER runs for the platform auth surface. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !StringUtils.hasText(request.getHeader(API_KEY_HEADER))
                || request.getRequestURI().startsWith(PLATFORM_AUTH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (tryAuthenticate(request.getHeader(API_KEY_HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ApiErrorFactory.of(ErrorCode.PLATFORM_API_KEY_INVALID, request.getRequestURI()));
    }

    private boolean tryAuthenticate(String rawKey) {
        int separator = rawKey.indexOf('_');
        if (separator <= 0) {
            return fail(null, null, null, "malformed_key");
        }
        if (TenantContext.getCurrentTenant().isPresent()) {
            // Platform identities are tenant-less (RISK-19 symmetry) — and on PG the tenant
            // schema has no t_platform_api_keys, so this check must precede any DB hit.
            return fail(null, null, null, "tenant_context_active");
        }
        String prefix = rawKey.substring(0, separator);
        Optional<PlatformApiKey> maybeKey = platformApiKeyRepository.findWithUserByKeyPrefix(prefix);
        if (maybeKey.isEmpty()) {
            return fail(null, null, null, "unknown_prefix");
        }
        PlatformApiKey key = maybeKey.get();
        if (!hashMatches(rawKey, key.getKeyHash())) {
            return fail(key.getPlatformUser().getId(), key.getId(), prefix, "hash_mismatch");
        }
        if (key.getRevokedAt() != null) {
            return fail(key.getPlatformUser().getId(), key.getId(), prefix, "key_revoked");
        }
        if (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(OffsetDateTime.now())) {
            return fail(key.getPlatformUser().getId(), key.getId(), prefix, "key_expired");
        }
        PlatformUser account = key.getPlatformUser();
        if (account.getUserType() != PlatformUserType.SERVICE) {
            return fail(account.getId(), key.getId(), prefix, "not_a_service_account");
        }
        if (!account.isEnabled()) {
            return fail(account.getId(), key.getId(), prefix, "account_disabled");
        }
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(OffsetDateTime.now())) {
            return fail(account.getId(), key.getId(), prefix, "account_locked");
        }

        Set<GrantedAuthority> authorities = Arrays.stream(key.getScopes().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        CustomUserDetails principal = new CustomUserDetails(account.getId(), account.getEmail(), null,
                true, true, true, true, authorities, null, null, JwtTokenProvider.SCOPE_PLATFORM);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        touchLastUsed(key);
        return true;
    }

    private boolean hashMatches(String rawKey, String storedHash) {
        return MessageDigest.isEqual(
                TokenHasher.sha256Hex(rawKey).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    /** Best-effort usage telemetry — a failed write must never fail the request. */
    private void touchLastUsed(PlatformApiKey key) {
        try {
            key.setLastUsedAt(OffsetDateTime.now());
            platformApiKeyRepository.save(key);
        } catch (RuntimeException ex) {
            log.debug("API key lastUsedAt update failed (best-effort): {}", ex.getMessage());
        }
    }

    private boolean fail(UUID accountId, UUID keyId, String prefix, String reason) {
        SecurityContextHolder.clearContext();
        log.debug("X-API-Key authentication rejected ({}): prefix={}, keyId={}", reason, prefix, keyId);
        platformAuditService.record(accountId,
                accountId != null ? PlatformAuditService.ACTOR_SERVICE : PlatformAuditService.ACTOR_SYSTEM,
                PlatformAuditService.ACTION_API_KEY_AUTH_FAILED, "PlatformApiKey", keyId, reason);
        return false;
    }
}
