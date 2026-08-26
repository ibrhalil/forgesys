package com.ibrhalil.forgesys.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RSA key material for RS256 under {@code jwt.rsa.*} — NEVER committed.
 * Generate: {@code openssl genrsa -out certs/private.pem 2048} then
 * {@code openssl rsa -in certs/private.pem -pubout -out certs/public.pem}.
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
