package com.ibrhalil.forgesys.service.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Template rendering: classpath defaults (TR/EN), placeholder substitution, subject
 * language selection and the {@code forgesys.mail.templates-dir} filesystem override.
 */
class MailTemplateRendererTest {

    private MailTemplateRenderer renderer(MailProperties properties) {
        return new MailTemplateRenderer(properties);
    }

    private MailMessage message() {
        return new MailMessage("ali@gmail.com", MailTemplate.TENANT_VERIFY,
                "http://app.local/verify-tenant?token=raw", "Ali", "Gebze Klübü", java.time.Duration.ofHours(24));
    }

    @Test
    void rendersClasspathTurkishDefaultWithPlaceholders() {
        MailTemplateRenderer.RenderedMail rendered =
                renderer(new MailProperties(null, null, null)).render(message());

        assertThat(rendered.subject()).isEqualTo("ForgeSys — organizasyonunuzu doğrulayın");
        assertThat(rendered.bodyHtml())
                .contains("Merhaba Ali")
                .contains("<strong>Gebze Klübü</strong>")
                .contains("24 saat geçerlidir")
                .contains("http://app.local/verify-tenant?token=raw")
                .doesNotContain("{{");
    }

    @Test
    void rendersEnglishWhenConfigured() {
        MailTemplateRenderer.RenderedMail rendered =
                renderer(new MailProperties(null, "en", null)).render(message());

        assertThat(rendered.subject()).isEqualTo("ForgeSys — verify your organization");
        assertThat(rendered.bodyHtml())
                .contains("Hello Ali")
                .contains("valid for 24 hours")
                .doesNotContain("{{");
    }

    @Test
    void nullPlaceholdersFallBackToEmptyOrGenericCopy() {
        MailMessage sparse = new MailMessage("a@b.com", MailTemplate.TENANT_VERIFY,
                "http://x/y", null, "Acme", null);

        MailTemplateRenderer.RenderedMail rendered =
                renderer(new MailProperties(null, "tr", null)).render(sparse);

        assertThat(rendered.bodyHtml()).contains("Merhaba").doesNotContain("{{");
    }

    @Test
    void templatesDirOverrideWinsWhenFileExists(@TempDir Path tempDir) throws Exception {
        Path override = tempDir.resolve("tenant-verify.tr.html");
        Files.writeString(override, "ÖZEL ŞABLON {{organizationName}} — {{actionUrl}}");

        MailTemplateRenderer.RenderedMail rendered =
                renderer(new MailProperties(null, "tr", tempDir.toString())).render(message());

        assertThat(rendered.bodyHtml()).isEqualTo("ÖZEL ŞABLON Gebze Klübü — http://app.local/verify-tenant?token=raw");
    }

    @Test
    void templatesDirMissingFileFallsBackToClasspath(@TempDir Path tempDir) {
        MailTemplateRenderer.RenderedMail rendered =
                renderer(new MailProperties(null, "tr", tempDir.toString())).render(message());

        assertThat(rendered.bodyHtml()).contains("Merhaba Ali").doesNotContain("{{");
    }

    @Test
    void everyShippedTemplateExistsForBothLanguagesAndRendersClean() {
        List.of("tr", "en").forEach(lang -> {
            for (MailTemplate template : MailTemplate.values()) {
                MailMessage msg = new MailMessage("a@b.com", template, "http://x/y", "A", "O", java.time.Duration.ofHours(1));
                MailTemplateRenderer.RenderedMail rendered =
                        renderer(new MailProperties(null, lang, null)).render(msg);
                assertThat(rendered.bodyHtml()).doesNotContain("{{");
            }
        });
    }

    @Test
    void unknownLanguageFallsBackToTurkish() {
        assertThat(MailTemplate.TENANT_VERIFY.subject("de"))
                .isEqualTo(MailTemplate.TENANT_VERIFY.subject("tr"));
    }
}
