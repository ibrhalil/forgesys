package com.ibrhalil.forgesys.security.jwt;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authenticates each request from the access-token cookie or Bearer header (RS256):
 * rebuilds the principal from claims (no DB hit), enforcing tenant binding (RISK-19),
 * user-scoped revoke ({@code tokenInvalidBefore}, RISK-21) and the per-{@code jti}
 * blacklist (K-34). Any failure clears the context → uniform 401.
 * rationale: docs/CODE_NOTES.md (backend/security → JwtAuthenticationFilter)
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;
    private final PlatformUserRepository platformUserRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final String cookieName;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder,
                                   UserRepository userRepository,
                                   PlatformUserRepository platformUserRepository,
                                   TokenBlacklistService tokenBlacklistService,
                                   @Value("${jwt.cookie-name:sf_access_token}") String cookieName) {
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
        this.platformUserRepository = platformUserRepository;
        this.tokenBlacklistService = tokenBlacklistService;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Jwt jwt = jwtDecoder.decode(token);
                authenticateIfTenantMatches(jwt);
            } catch (JwtException e) {
                log.debug("Invalid JWT rejected: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * [RISK-19] JWT {@code tenant} claim must equal the request tenant; the principal's
     * {@code tenantSchema} is taken from the context, never the claim. K-50: a
     * {@code scope=platform} token takes the platform branch instead (no tenant claim;
     * valid only where NO tenant context was resolved).
     */
    private void authenticateIfTenantMatches(Jwt jwt) {
        if (!hasValidIssuerAndAudience(jwt)) {
            SecurityContextHolder.clearContext();
            return;
        }
        if (JwtTokenProvider.SCOPE_PLATFORM.equals(jwt.getClaimAsString(JwtTokenProvider.CLAIM_SCOPE))) {
            authenticatePlatform(jwt);
            return;
        }
        String ctxTenant = TenantContext.getCurrentTenant().orElse("public");
        String jwtTenant = jwt.getClaimAsString(JwtTokenProvider.CLAIM_TENANT);
        if (!StringUtils.hasText(jwtTenant)) {
            jwtTenant = "public";
        }
        if (!jwtTenant.equals(ctxTenant)) {
            log.debug("JWT tenant claim [{}] does not match request tenant [{}]; rejecting", jwtTenant, ctxTenant);
            SecurityContextHolder.clearContext();
            return;
        }
        if (isRevokedByTokenInvalidBefore(jwt)) {
            SecurityContextHolder.clearContext();
            return;
        }
        if (isBlacklisted(jwt)) {
            SecurityContextHolder.clearContext();
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(toAuthentication(jwt, ctxTenant));
    }

    /**
     * K-50: platform-identity token. Valid only on tenant-less requests (context empty
     * → "public") — replay on a tenant subdomain is rejected. Revocation resolves
     * against {@code t_platform_users}, never the tenant {@code t_users}.
     */
    private void authenticatePlatform(Jwt jwt) {
        String ctxTenant = TenantContext.getCurrentTenant().orElse("public");
        if (!"public".equals(ctxTenant)) {
            log.debug("Platform JWT presented on tenant context [{}]; rejecting", ctxTenant);
            SecurityContextHolder.clearContext();
            return;
        }
        if (isRevokedByPlatformTokenInvalidBefore(jwt)) {
            SecurityContextHolder.clearContext();
            return;
        }
        if (isBlacklisted(jwt)) {
            SecurityContextHolder.clearContext();
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(toPlatformAuthentication(jwt));
    }

    /**
     * [RISK-21 / K-50] Platform twin of {@link #isRevokedByTokenInvalidBefore(Jwt)} —
     * checks {@code t_platform_users.tokenInvalidBefore} instead of the tenant account.
     */
    private boolean isRevokedByPlatformTokenInvalidBefore(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        Optional<OffsetDateTime> maybeInvalidBefore = platformUserRepository.findTokenInvalidBefore(userId);
        if (maybeInvalidBefore.isEmpty()) {
            return false;
        }
        Instant issuedAt = jwt.getIssuedAt();
        if (issuedAt == null) {
            return false;
        }
        Instant invalidBefore = maybeInvalidBefore.get().toInstant().truncatedTo(ChronoUnit.SECONDS);
        return issuedAt.isBefore(invalidBefore);
    }

    /**
     * [RISK-21] Rejects tokens whose {@code iat} predates {@code UserAccount.tokenInvalidBefore}
     * (absent row/column/iat = accept). The stamp is truncated to whole seconds before the
     * compare — a naive compare would reject a token minted in the same second as the revoke
     * (iat floors to seconds, the timestamptz keeps nanos) and break fast re-login.
     */
    private boolean isRevokedByTokenInvalidBefore(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            // Malformed subject cannot match an account — pass as anonymous;
            // downstream @PreAuthorize rejects unknown users.
            return false;
        }
        Optional<OffsetDateTime> maybeInvalidBefore = userRepository.findTokenInvalidBefore(userId);
        if (maybeInvalidBefore.isEmpty()) {
            return false;
        }
        Instant issuedAt = jwt.getIssuedAt();
        if (issuedAt == null) {
            return false;
        }
        Instant invalidBefore = maybeInvalidBefore.get().toInstant().truncatedTo(ChronoUnit.SECONDS);
        boolean revoked = issuedAt.isBefore(invalidBefore);
        if (revoked) {
            log.debug("JWT for user {} issued at {} predates tokenInvalidBefore {}; rejecting",
                    userId, issuedAt, invalidBefore);
        }
        return revoked;
    }

    /**
     * [K-34] Granular per-token revoke; tokens without a {@code jti} bypass this —
     * they still face the user-scoped {@code tokenInvalidBefore} check.
     */
    private boolean isBlacklisted(Jwt jwt) {
        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            return false;
        }
        if (tokenBlacklistService.isBlacklisted(jti)) {
            log.debug("JWT jti {} is blacklisted (per-session logout); rejecting", jti);
            return true;
        }
        return false;
    }

    /** Rejects tokens whose iss/aud don't match this deployment (foreign/malformed). */
    private boolean hasValidIssuerAndAudience(Jwt jwt) {
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        if (!JwtTokenProvider.ISSUER.equals(issuer)) {
            log.debug("JWT issuer [{}] rejected", issuer);
            return false;
        }
        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(JwtTokenProvider.AUDIENCE)) {
            log.debug("JWT audience {} rejected", audience);
            return false;
        }
        return true;
    }

    private String extractToken(HttpServletRequest request) {
        // Cookie takes precedence over the Bearer header (browser sessions first).
        // K-50: the platform cookie (path-scoped to /api/v1/platform) wins when both
        // are present — on platform paths the browser may send the tenant cookie too
        // (path /), but the platform identity is the intended principal there. Tenant
        // endpoints never receive the platform cookie (path isolation).
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String platformCookie = readCookie(cookies, PlatformAuthProperties.ACCESS_COOKIE_NAME);
            if (platformCookie != null) {
                return platformCookie;
            }
            String cookieToken = readCookie(cookies, cookieName);
            if (cookieToken != null) {
                return cookieToken;
            }
        }
        String bearerHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerHeader) && bearerHeader.startsWith("Bearer ")) {
            return bearerHeader.substring(7);
        }
        return null;
    }

    private static String readCookie(Cookie[] cookies, String name) {
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private UsernamePasswordAuthenticationToken toAuthentication(Jwt jwt, String tenantSchema) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString(JwtTokenProvider.CLAIM_EMAIL);
        List<String> authorityNames = jwt.getClaimAsStringList(JwtTokenProvider.CLAIM_AUTHORITIES);
        Set<GrantedAuthority> authorities = (authorityNames == null ? List.<String>of() : authorityNames).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        // K-50 F6: impersonation claims ride along (banner data + act-aware auditing).
        String actUserId = jwt.getClaimAsString(JwtTokenProvider.CLAIM_ACT);
        String actEmail = jwt.getClaimAsString(JwtTokenProvider.CLAIM_ACTOR_EMAIL);
        boolean impersonation = actUserId != null
                && Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtTokenProvider.CLAIM_IMPERSONATION));

        CustomUserDetails principal = new CustomUserDetails(userId, email, null, true, true, true, true,
                authorities, tenantSchema, jwt.getId(), null, actUserId, actEmail, impersonation);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    /** K-50: principal rebuilt from a platform-identity token (no tenant schema). */
    @SuppressWarnings("unchecked")
    private UsernamePasswordAuthenticationToken toPlatformAuthentication(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString(JwtTokenProvider.CLAIM_EMAIL);
        List<String> authorityNames = jwt.getClaimAsStringList(JwtTokenProvider.CLAIM_AUTHORITIES);
        Set<GrantedAuthority> authorities = (authorityNames == null ? List.<String>of() : authorityNames).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        CustomUserDetails principal = new CustomUserDetails(userId, email, null, true, true, true, true,
                authorities, null, jwt.getId(), JwtTokenProvider.SCOPE_PLATFORM);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
