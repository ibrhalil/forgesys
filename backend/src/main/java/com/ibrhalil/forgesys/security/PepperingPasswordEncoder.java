package com.ibrhalil.forgesys.security;

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
 * BCrypt keyed with a global pepper via HMAC-SHA256 pre-hash (OWASP strategy, K-23):
 * raw password → HMAC-SHA256(pepper) → Base64 (44 chars, under BCrypt's 72-byte limit)
 * → BCrypt(12). Legacy pepper-less {@code $2a$12$...} hashes still validate and rehash
 * lazily on next login; peppered hashes carry the {@code {sf-peppered}} marker. Pepper
 * rotation is deliberately unsupported.
 * rationale: docs/CODE_NOTES.md (backend/security → PepperingPasswordEncoder)
 */
public class PepperingPasswordEncoder implements PasswordEncoder {

    /** Marker distinguishing peppered hashes from legacy pepper-less BCrypt. */
    static final String PEPPERED_MARKER = "{sf-peppered}";

    private final BCryptPasswordEncoder bcrypt;
    private final byte[] pepper;

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

    /** True for legacy pepper-less hashes — signals the login flow to rehash lazily. */
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
