package com.ibrhalil.forgesys.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hex digest for tokens persisted at rest (K-34 refresh tokens, RISK-30
 * verification tokens): only the digest is stored, so a store leak cannot replay it.
 * Lowercase hex, matching PostgreSQL's {@code encode(sha256(x), 'hex')} (public V3 backfill).
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
