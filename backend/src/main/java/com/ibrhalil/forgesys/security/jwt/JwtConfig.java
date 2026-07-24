package com.ibrhalil.forgesys.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * JWT infrastructure (RS256). Produces a shared {@link KeyPair} (so the encoder and
 * decoder always agree), plus {@link JwtEncoder}/{@link JwtDecoder} beans.
 *
 * <p>The oauth2-resource-server auto-config filter is intentionally NOT enabled
 * ([RISK-14](../../../../../docs/DECISIONS.md#risk-14)) — a custom
 * {@code JwtAuthenticationFilter} handles decoding + context population so that
 * revocation (tokenInvalidBefore / Redis blacklist) can be layered on later.
 */
@Configuration
@EnableConfigurationProperties(RsaKeyProperties.class)
public class JwtConfig {

    public static final String KEY_ID = "forgesys-rs256";

    @Bean
    public KeyPair jwtRsaKeyPair(RsaKeyProperties properties, Environment environment) {
        // [RISK-23] prod must fail fast on missing RSA keys; dev/test fall back to ephemeral.
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        return RsaKeys.resolve(properties, prod);
    }

    @Bean
    public JwtEncoder jwtEncoder(KeyPair jwtRsaKeyPair) {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) jwtRsaKeyPair.getPublic())
                .privateKey((RSAPrivateKey) jwtRsaKeyPair.getPrivate())
                .keyID(KEY_ID)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    @Bean
    public JwtDecoder jwtDecoder(KeyPair jwtRsaKeyPair) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtRsaKeyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }
}
