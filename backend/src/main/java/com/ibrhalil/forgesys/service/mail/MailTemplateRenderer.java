package com.ibrhalil.forgesys.service.mail;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders {@link MailTemplate} HTML bodies. Lookup per template: the
 * {@code forgesys.mail.templates-dir} override first, then the classpath default.
 * Placeholders are plain {@code {{token}}} string replacement — deliberately no
 * expression language, so template content can never execute code.
 */
@Component
public class MailTemplateRenderer {

    private final MailProperties properties;

    public MailTemplateRenderer(MailProperties properties) {
        this.properties = properties;
    }

    public RenderedMail render(MailMessage message) {
        String language = message.language() != null && !message.language().isBlank()
                ? message.language()
                : properties.effectiveLanguage();
        String subject = message.template().subject(language);
        String expiresInHours = message.expiresIn() == null ? "" : String.valueOf(message.expiresIn().toHours());
        String expiresInMinutes = message.expiresIn() == null ? "" : String.valueOf(message.expiresIn().toMinutes());
        String html = loadTemplate(message.template().key(), language)
                .replace("{{firstName}}", safe(message.firstName()))
                .replace("{{organizationName}}", safe(message.organizationName()))
                .replace("{{actionUrl}}", safe(message.actionUrl()))
                .replace("{{expiresInHours}}", expiresInHours)
                .replace("{{expiresInMinutes}}", expiresInMinutes);
        return new RenderedMail(subject, html);
    }

    private String loadTemplate(String key, String language) {
        String fileName = key + "." + language + ".html";
        if (properties.templatesDir() != null && !properties.templatesDir().isBlank()) {
            Path override = Path.of(properties.templatesDir(), fileName);
            if (Files.exists(override)) {
                return readFile(override);
            }
            // Fall through to classpath — a missing override must not silently disable a mail.
        }
        try {
            ClassPathResource resource = new ClassPathResource("mail/" + fileName);
            return new String(FileCopyUtils.copyToByteArray(resource.getInputStream()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Mail template not found on classpath: mail/" + fileName, e);
        }
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read mail template override: " + path, e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** A fully rendered mail: final subject + HTML body with placeholders substituted. */
    public record RenderedMail(String subject, String bodyHtml) {
    }
}
