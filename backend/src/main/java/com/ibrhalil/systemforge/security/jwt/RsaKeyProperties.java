package com.ibrhalil.systemforge.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RSA key material for RS256 JWT signing/verification, loaded from externalized
 * config (e.g. {@code certs/*.pem} in prod). When neither PEM is configured, the
 * {@code JwtConfig} falls back to an ephemeral key pair (dev/test) — see
 * {@link RsaKeys#resolve(RsaKeyProperties)}.
 *
 * <p>Keys are NEVER committed (AGENTS "Never" rule). Generate with:
 * <pre>
 * openssl genrsa -out certs/private.pem 2048
 * openssl rsa -in certs/private.pem -pubout -out certs/public.pem
 * </pre>
 */
@ConfigurationProperties(prefix = "jwt.rsa")
public record RsaKeyProperties(
        String privateKeyPem,
        String publicKeyPem
) {
    public boolean isConfigured() {
        return privateKeyPem != null && !privateKeyPem.isBlank()
                && publicKeyPem != null && !publicKeyPem.isBlank();
    }
}
