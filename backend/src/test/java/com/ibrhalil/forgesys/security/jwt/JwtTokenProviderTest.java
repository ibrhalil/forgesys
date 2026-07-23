package com.ibrhalil.forgesys.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import java.security.KeyPair;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trip test for {@link JwtTokenProvider}: a token minted by the provider is
 * decodable by the matching decoder and carries the expected claims. Uses the same
 * ephemeral key-pair path as dev/test (no cert files required).
 */
class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        KeyPair keyPair = RsaKeys.resolve(new RsaKeyProperties(null, null));
        RSAKey rsaKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                .keyID(JwtConfig.KEY_ID)
                .build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        jwtDecoder = NimbusJwtDecoder.withPublicKey((java.security.interfaces.RSAPublicKey) keyPair.getPublic()).build();
        tokenProvider = new JwtTokenProvider(encoder, 15);
    }

    @Test
    void generatedTokenDecodesWithExpectedClaims() {
        String token = tokenProvider.generateAccessToken(
                "11111111-1111-1111-1111-111111111111",
                "admin@acme.com",
                "tenant_acme",
                List.of("iam:user:write", "iam:user:read"));

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(jwt.getClaimAsString(JwtTokenProvider.CLAIM_EMAIL)).isEqualTo("admin@acme.com");
        assertThat(jwt.getClaimAsString(JwtTokenProvider.CLAIM_TENANT)).isEqualTo("tenant_acme");
        assertThat(jwt.getClaimAsStringList(JwtTokenProvider.CLAIM_AUTHORITIES))
                .containsExactlyInAnyOrder("iam:user:write", "iam:user:read");
        assertThat(jwt.getIssuer().toString()).isEqualTo(JwtTokenProvider.ISSUER);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = tokenProvider.generateAccessToken(
                "22222222-2222-2222-2222-222222222222", "x@y.com", "tenant_x", List.of());

        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThatThrownBy(() -> jwtDecoder.decode(tampered)).isInstanceOf(Exception.class);
    }

    @Test
    void expiredTokenIsRejected() {
        // Build a provider with 0-minute TTL so the token is already at its boundary;
        // a token whose exp is in the past must be rejected on decode.
        KeyPair keyPair = RsaKeys.resolve(new RsaKeyProperties(null, null));
        RSAKey rsaKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                .keyID(JwtConfig.KEY_ID)
                .build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey((java.security.interfaces.RSAPublicKey) keyPair.getPublic()).build();

        Instant pastExpiry = Instant.now().minusSeconds(60);
        org.springframework.security.oauth2.jwt.JwtClaimsSet claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer(JwtTokenProvider.ISSUER)
                .issuedAt(Instant.now().minusSeconds(120))
                .expiresAt(pastExpiry)
                .subject("3")
                .build();
        String expired = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(claims)).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(Exception.class);
    }
}
