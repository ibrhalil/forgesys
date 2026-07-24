package com.ibrhalil.forgesys.security.jwt;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [RISK-23] RSA key resolution fail-fast behavior. In the prod profile, missing keys
 * must abort startup (no silent ephemeral fallback); in dev/test an ephemeral pair is
 * generated. Configured PEMs are parsed regardless of profile.
 */
class RsaKeysTest {

    @Test
    void unconfiguredInProdThrowsFailFast() {
        assertThatThrownBy(() -> RsaKeys.resolve(new RsaKeyProperties(null, null), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void unconfiguredInNonProdReturnsEphemeralPair() {
        KeyPair keyPair = RsaKeys.resolve(new RsaKeyProperties(null, null), false);

        assertThat(keyPair).isNotNull();
        assertThat(keyPair.getPublic()).isNotNull();
        assertThat(keyPair.getPrivate()).isNotNull();
    }

    @Test
    void configuredKeysAreParsedEvenWhenProdStrict() {
        KeyPair seed = RsaKeys.generateEphemeral();
        String publicPem = toPem("PUBLIC KEY", seed.getPublic().getEncoded());
        String privatePem = toPem("PRIVATE KEY", seed.getPrivate().getEncoded());

        KeyPair resolved = RsaKeys.resolve(new RsaKeyProperties(privatePem, publicPem), true);

        assertThat(resolved.getPublic().getEncoded()).isEqualTo(seed.getPublic().getEncoded());
        assertThat(resolved.getPrivate().getEncoded()).isEqualTo(seed.getPrivate().getEncoded());
    }

    private static String toPem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getEncoder().encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
