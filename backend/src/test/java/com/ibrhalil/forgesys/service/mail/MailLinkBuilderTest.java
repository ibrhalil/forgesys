package com.ibrhalil.forgesys.service.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant-anchored link derivation from {@code app-base-url}: subdomain prefix onto the
 * host, explicit port kept (dev), no port for default schemes (prod).
 */
class MailLinkBuilderTest {

    private MailLinkBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new MailLinkBuilder();
    }

    @Test
    void devBaseUrlKeepsExplicitPortAndPrefixesSubdomain() {
        ReflectionTestUtils.setField(builder, "appBaseUrl", "http://localhost:3000");

        String link = builder.tenantLink("tenant_acme", "/verify-email", "raw-token");

        assertThat(link).isEqualTo("http://acme.localhost:3000/verify-email?token=raw-token");
    }

    @Test
    void prodBaseUrlHasNoPortAndRestoresDashesFromSchemaUnderscores() {
        ReflectionTestUtils.setField(builder, "appBaseUrl", "https://app.example.com");

        String link = builder.tenantLink("tenant_geba_klubu", "/reset-password", "t");

        // The schema fold (_ for -) is inverted; unique because subdomains reject _.
        assertThat(link).isEqualTo("https://geba-klubu.app.example.com/reset-password?token=t");
    }
}
