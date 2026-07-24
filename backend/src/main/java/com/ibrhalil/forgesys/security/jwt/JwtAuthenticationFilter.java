package com.ibrhalil.forgesys.security.jwt;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
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
import org.springframework.security.core.context.SecurityContext;
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
 * Authenticates each request from the access-token cookie (RS256). Decodes the JWT,
 * rebuilds the {@link CustomUserDetails} principal + authorities from claims (no DB
 * hit), and populates the {@link SecurityContext}.
 *
 * <p><strong>Tenant binding ([RISK-19](../../../../../../docs/DECISIONS.md#risk-19)):</strong>
 * the JWT {@code tenant} claim (schema minted at login) MUST equal the schema resolved
 * for the request by {@code TenantFilter} (read from {@link TenantContext}). A token
 * minted for tenant A replayed against tenant B (cross-tenant privilege escalation)
 * is rejected by clearing the context (→ 401). When the request carries no tenant
 * (exempt/public), both sides normalize to {@code "public"}.
 *
 * <p>On any decode failure (bad signature/expired/malformed) the context is cleared
 * and the request proceeds unauthenticated; protected routes then get a uniform 401
 * from {@code RestAuthenticationEntryPoint}.
 *
 * <p><strong>Revocation ([RISK-21](../../../../../../docs/DECISIONS.md#risk-21)):</strong>
 * after the tenant binding check, the filter reads {@code UserAccount.tokenInvalidBefore}
 * from the tenant schema (single-column projection — no JOIN, no lazy proxy). A token
 * whose {@code iat} predates {@code tokenInvalidBefore} was issued before the user
 * changed/reset their password or logged out, so it is rejected by clearing the
 * context (→ 401). Redis-backed access-token blacklist (granular revoke) is still
 * deferred to Epic 2.6; until then revocation is user-scoped (all of the user's
 * outstanding tokens) and per-request cost is one small indexed query.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;
    private final String cookieName;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder,
                                   UserRepository userRepository,
                                   @Value("${jwt.cookie-name:sf_access_token}") String cookieName) {
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
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
     * [RISK-19] Binds the token to the request tenant: the JWT {@code tenant} claim
     * must equal the schema resolved for this request by {@code TenantFilter} (read
     * from {@link TenantContext}). A mismatch clears the context so the request
     * proceeds unauthenticated (→ 401). The principal's {@code tenantSchema} is taken
     * from the context, never the claim.
     *
     * <p>[RISK-21] After the tenant check, {@code tokenInvalidBefore} is consulted: a
     * token issued before that timestamp (password change/reset, logout, brute-force
     * lockout by a future extension) is rejected by clearing the context (→ 401).
     */
    private void authenticateIfTenantMatches(Jwt jwt) {
        if (!hasValidIssuerAndAudience(jwt)) {
            SecurityContextHolder.clearContext();
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
        SecurityContextHolder.getContext().setAuthentication(toAuthentication(jwt, ctxTenant));
    }

    /**
     * [RISK-21] Reads {@code UserAccount.tokenInvalidBefore} for the token subject from
     * the request's tenant schema and rejects tokens minted before it. Returns
     * {@code false} (accept) when the account row is absent, the column is null, or the
     * JWT lacks an {@code iat} claim — the narrow reject case is a present
     * {@code tokenInvalidBefore} strictly after the token's issued-at instant.
     *
     * <p>Resolution note: JWT {@code iat} is a NumericDate (seconds), while
     * {@code tokenInvalidBefore} is a {@code timestamptz} (sub-second). A naive
     * {@code iat < tokenInvalidBefore} would reject a token minted in the same second
     * as a password change/logout (iat floors to the second, the timestamp keeps its
     * nanos), which would also break a fast re-login. {@code tokenInvalidBefore} is
     * floored to the second before the compare, so only a token whose iat second is
     * strictly earlier than the revoke second is rejected.
     */
    private boolean isRevokedByTokenInvalidBefore(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            // Malformed subject cannot match a user account — let it pass as anonymous
            // here; the downstream @PreAuthorize layer rejects unknown users.
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
     * Validates the standard {@code iss}/{@code aud} claims so a token minted by another
     * system (even if it happened to share the key) or a malformed token is rejected.
     */
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
        // 1. Cookie-based auth (browser sessions)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String cookieToken = Arrays.stream(cookies)
                    .filter(c -> cookieName.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
            if (cookieToken != null) {
                return cookieToken;
            }
        }
        // 2. Bearer token header (API clients, cURL, jobs)
        String bearerHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerHeader) && bearerHeader.startsWith("Bearer ")) {
            return bearerHeader.substring(7);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private UsernamePasswordAuthenticationToken toAuthentication(Jwt jwt, String tenantSchema) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString(JwtTokenProvider.CLAIM_EMAIL);
        List<String> authorityNames = jwt.getClaimAsStringList(JwtTokenProvider.CLAIM_AUTHORITIES);
        Set<GrantedAuthority> authorities = (authorityNames == null ? List.<String>of() : authorityNames).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        CustomUserDetails principal = new CustomUserDetails(userId, email, null, true, true, true, true, authorities, tenantSchema);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
