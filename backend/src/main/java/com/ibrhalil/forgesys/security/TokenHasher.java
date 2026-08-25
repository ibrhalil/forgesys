package com.ibrhalil.forgesys.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hex digest helper for opaque tokens persisted at rest (K-34 refresh tokens,
 * [RISK-30] verification tokens). The raw token only ever lives in its delivery
 * channel (email link, cookie, response body); every persisted form — DB column or
 * Redis key/value — carries the digest, so a store leak cannot replay it. Digests are
 * lowercase hex, matching PostgreSQL's {@code encode(sha256(x), 'hex')} (public V3
 * backfill) and the former per-store private copies this utility replaces.
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
