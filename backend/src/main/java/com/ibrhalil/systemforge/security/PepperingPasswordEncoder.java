package com.ibrhalil.systemforge.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.Assert;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * BCrypt password encoder wrapped with a global pepper (HMAC pre-hash strategy).
 *
 * <p>BCrypt has no native pepper support, so a raw password is first run through
 * HMAC-SHA256 keyed with the pepper, Base64-encoded (32 bytes -> 44 chars, well under
 * BCrypt's 72-byte limit), and that digest is what BCrypt actually hashes. This is the
 * OWASP-recommended pepper strategy for BCrypt. A database leak alone is now
 * insufficient to brute-force hashes — the pepper (held outside the DB) is required.
 *
 * <h2>Hash formats &amp; lazy migration</h2>
 * <ul>
 *   <li><strong>Legacy (pepper-less):</strong> a plain BCrypt hash
 *       {@code $2a$12$...} produced by the previous {@code BCryptPasswordEncoder(12)}
 *       bean (RISK-13). These remain valid: {@link #matches(CharSequence, String)}
 *       detects the absence of the marker and verifies the raw password directly
 *       against BCrypt. {@link #upgradeEncoding(String)} returns {@code true} so the
 *       login flow rehashes them with pepper on the next successful login (lazy
 *       migration, same philosophy as RISK-13).</li>
 *   <li><strong>Peppered:</strong> {@code {sf-peppered}$2a$12$...}. The
 *       {@code {sf-peppered}} marker distinguishes a peppered hash from a legacy one
 *       at verify time (both otherwise look like BCrypt). New encodings always use
 *       this format.</li>
 * </ul>
 *
 * <p><strong>Pepper rotation</strong> is intentionally not supported: a pepper change
 * invalidates every peppered hash (legacy hashes would still verify, then rehash to
 * the new pepper). If rotation is ever needed, a dedicated migration flow must be
 * designed.
 *
 * @see org.springframework.security.crypto.password.PasswordEncoder#upgradeEncoding(String)
 */
public class PepperingPasswordEncoder implements PasswordEncoder {

    /** Marker prefix prepended to peppered hashes so {@link #matches} can tell them
     *  apart from legacy pepper-less BCrypt hashes. */
    static final String PEPPERED_MARKER = "{sf-peppered}";

    private final BCryptPasswordEncoder bcrypt;
    private final byte[] pepper;

    /**
     * @param pepper  the global pepper (secret, held outside the DB). Must be non-blank.
     * @param strength BCrypt cost factor (12 — RISK-13).
     */
    public PepperingPasswordEncoder(String pepper, int strength) {
        Assert.hasText(pepper, "pepper must not be blank");
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        this.bcrypt = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        String peppered = pepperHash(rawPassword.toString());
        return PEPPERED_MARKER + bcrypt.encode(peppered);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        if (encodedPassword.startsWith(PEPPERED_MARKER)) {
            String bcryptPart = encodedPassword.substring(PEPPERED_MARKER.length());
            return bcrypt.matches(pepperHash(rawPassword.toString()), bcryptPart);
        }
        // Legacy pepper-less BCrypt hash (pre-K-23) — verify raw password directly.
        return bcrypt.matches(rawPassword, encodedPassword);
    }

    /**
     * Returns {@code true} when the stored hash is a legacy pepper-less BCrypt hash,
     * signalling the login flow to rehash it with pepper (lazy migration). Peppered
     * hashes return {@code false} (already current).
     */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null && !encodedPassword.startsWith(PEPPERED_MARKER);
    }

    private String pepperHash(String rawPassword) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            byte[] hmac = mac.doFinal(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable — JCA misconfigured", e);
        }
    }
}
