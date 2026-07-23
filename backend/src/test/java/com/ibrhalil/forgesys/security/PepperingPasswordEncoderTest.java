package com.ibrhalil.forgesys.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PepperingPasswordEncoder}: HMAC pre-hash correctness, the
 * {@code {sf-peppered}} marker, and — critically — that legacy pepper-less BCrypt
 * hashes (pre-K-23) still verify (lazy-migration contract).
 */
class PepperingPasswordEncoderTest {

    private final PepperingPasswordEncoder encoder = new PepperingPasswordEncoder("test-pepper-secret", 12);

    @Test
    void encodeProducesPepperedMarker() {
        String hash = encoder.encode("password123");
        assertThat(hash).startsWith(PepperingPasswordEncoder.PEPPERED_MARKER);
        assertThat(hash).contains("$2a$12$");
    }

    @Test
    void matchesAcceptsCorrectPassword() {
        String hash = encoder.encode("password123");
        assertThat(encoder.matches("password123", hash)).isTrue();
    }

    @Test
    void matchesRejectsWrongPassword() {
        String hash = encoder.encode("password123");
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }

    @Test
    void matchesRejectsBlankOrNullStoredHash() {
        assertThat(encoder.matches("password123", null)).isFalse();
        assertThat(encoder.matches("password123", "")).isFalse();
        assertThat(encoder.matches("password123", "   ")).isFalse();
    }

    @Test
    void encodeIsSaltedSoTwoCallsDiffer() {
        String a = encoder.encode("same");
        String b = encoder.encode("same");
        assertThat(a).isNotEqualTo(b);
        assertThat(encoder.matches("same", a)).isTrue();
        assertThat(encoder.matches("same", b)).isTrue();
    }

    @Test
    void differentPeppersProduceIncompatibleHashes() {
        PepperingPasswordEncoder other = new PepperingPasswordEncoder("different-pepper", 12);
        String hash = encoder.encode("password123");
        // The peppered hash cannot be verified with a different pepper.
        assertThat(other.matches("password123", hash)).isFalse();
    }

    @Test
    void matchesVerifiesLegacyPepperlessBcryptHash() {
        // A hash produced by the pre-K-23 BCryptPasswordEncoder(12) bean — no marker.
        String legacyHash = new BCryptPasswordEncoder(12).encode("legacy-password");
        assertThat(legacyHash).startsWith("$2a$12$");
        assertThat(encoder.matches("legacy-password", legacyHash)).isTrue();
        assertThat(encoder.matches("wrong", legacyHash)).isFalse();
    }

    @Test
    void upgradeEncodingTrueForLegacyAndFalseForPeppered() {
        String peppered = encoder.encode("password123");
        String legacy = new BCryptPasswordEncoder(12).encode("password123");

        assertThat(encoder.upgradeEncoding(peppered)).isFalse();
        assertThat(encoder.upgradeEncoding(legacy)).isTrue();
    }

    @Test
    void blankPepperRejected() {
        assertThatThrownBy(() -> new PepperingPasswordEncoder("", 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PepperingPasswordEncoder("   ", 12))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
