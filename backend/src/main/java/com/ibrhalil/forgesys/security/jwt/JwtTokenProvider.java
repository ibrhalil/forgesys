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

/**
 * Mints RS256 access tokens. Claims: {@code sub} (userId), {@code email},
 * {@code tenant} (schema), {@code authorities} (resolved permission strings),
 * plus standard {@code iss}/{@code iat}/{@code exp}. The authorities are embedded
 * so the auth filter need not hit the DB on every request; permission changes take
 * effect on the next token issue (login/refresh).
 */
@Component
public class JwtTokenProvider {

    public static final String ISSUER = "https://forgesys.dev";
    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_TENANT = "tenant";
    public static final String CLAIM_EMAIL = "email";

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
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES))
                .subject(userId)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_AUTHORITIES, authorities == null ? List.of() : authorities);
        // The tenant claim is only present when a tenant was resolved (login always
        // resolves one via subdomain; omitted claim keeps the builder happy otherwise).
        if (tenantSchema != null) {
            builder.claim(CLAIM_TENANT, tenantSchema);
        }
        JwtClaimsSet claims = builder.build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getAccessTokenTtlMinutes() {
        return accessTokenTtlMinutes;
    }
}
