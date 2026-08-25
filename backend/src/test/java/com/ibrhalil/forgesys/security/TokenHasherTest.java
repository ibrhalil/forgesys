package com.ibrhalil.forgesys.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [RISK-30] The digest must match the known SHA-256 test vector — it is the same
 * computation as PostgreSQL's {@code encode(sha256(x::bytea), 'hex')} used by the
 * public V3 backfill, so any drift breaks lookup of outstanding links.
 */
class TokenHasherTest {

    @Test
    void sha256Hex_matchesKnownVector() {
        assertThat(TokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Hex_isDeterministicAndLowercaseHex() {
        String raw = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6";
        String digest = TokenHasher.sha256Hex(raw);
        assertThat(digest).hasSize(64).matches("^[0-9a-f]+$");
        assertThat(TokenHasher.sha256Hex(raw)).isEqualTo(digest);
        assertThat(digest).isNotEqualTo(raw);
    }
}
