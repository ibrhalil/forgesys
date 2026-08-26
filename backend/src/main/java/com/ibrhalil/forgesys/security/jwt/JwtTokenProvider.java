package com.ibrhalil.forgesys.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Mints RS256 access tokens: sub (userId), jti (blacklist target, K-34), email, tenant,
 * authorities + iss/iat/exp. Authorities are embedded so the filter skips the DB;
 * permission changes apply on the next token issue (login/refresh).
 */
@Component
public class JwtTokenProvider {

    public static final String ISSUER = "https://forgesys.dev";
    /** Intended audience for every access token; validated by {@code JwtAuthenticationFilter}. */
    public static final String AUDIENCE = "forgesys";
    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_TENANT = "tenant";
    public static final String CLAIM_EMAIL = "email";
    /** JWT id claim — unique per token, the granular-blacklist key (K-34). */
    public static final String CLAIM_JTI = "jti";
    /** K-50: token scope — {@code platform} marks a platform-identity token (no tenant claim). */
    public static final String CLAIM_SCOPE = "scope";
    public static final String SCOPE_PLATFORM = "platform";

    private final JwtEncoder jwtEncoder;
    private final long accessTokenTtlMinutes;

    public JwtTokenProvider(JwtEncoder jwtEncoder,
                            @Value("${jwt.access-token-ttl-minutes:15}") long accessTokenTtlMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }

    public String generateAccessToken(String userId, String email, String tenantSchema, List<String> authorities) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES))
                .subject(userId)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_AUTHORITIES, authorities == null ? List.of() : authorities);
        // Tenant claim present only when a tenant was resolved (login always resolves one).
        if (tenantSchema != null) {
            builder.claim(CLAIM_TENANT, tenantSchema);
        }
        JwtClaimsSet claims = builder.build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * K-50: platform-identity token — carries {@code scope=platform} and NO tenant claim;
     * revocation/account checks resolve against {@code t_platform_users}.
     */
    public String generatePlatformAccessToken(String userId, String email, List<String> authorities) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES))
                .subject(userId)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_SCOPE, SCOPE_PLATFORM)
                .claim(CLAIM_AUTHORITIES, authorities == null ? List.of() : authorities)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getAccessTokenTtlMinutes() {
        return accessTokenTtlMinutes;
    }
}
