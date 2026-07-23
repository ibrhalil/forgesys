package com.ibrhalil.forgesys.security.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Resolves the RSA key pair used for JWT signing/verification.
 *
 * <p>If both PEMs are configured (prod), they are parsed into an {@link KeyPair}.
 * Otherwise (dev/test) an ephemeral 2048-bit key pair is generated — convenient for
 * local development and tests, but tokens do not survive a restart and must never be
 * used in production (a warning is logged).
 */
public final class RsaKeys {

    private static final Logger log = LoggerFactory.getLogger(RsaKeys.class);

    private RsaKeys() {
    }

    public static KeyPair resolve(RsaKeyProperties properties) {
        if (properties != null && properties.isConfigured()) {
            return new KeyPair(parsePublicKey(properties.publicKeyPem()), parsePrivateKey(properties.privateKeyPem()));
        }
        log.warn("No RSA keys configured (jwt.rsa.private-key-pem / public-key-pem). "
                + "Generating an EPHEMERAL key pair — tokens will not survive a restart. "
                + "Configure persistent keys for dev/prod.");
        return generateEphemeral();
    }

    static KeyPair generateEphemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation failed", e);
        }
    }

    static RSAPublicKey parsePublicKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse RSA public key PEM", e);
        }
    }

    static RSAPrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse RSA private key PEM", e);
        }
    }

    private static byte[] decodePem(String pem, String type) {
        return Base64.getDecoder().decode(
                pem.replace("-----BEGIN " + type + "-----", "")
                        .replace("-----END " + type + "-----", "")
                        .replaceAll("\\s", ""));
    }
}
