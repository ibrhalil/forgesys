package com.ibrhalil.forgesys.service.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound under {@code forgesys.mail.*}.
 *
 * @param from            RFC 822 From header value
 * @param defaultLanguage template language until per-user preferences exist (tr/en)
 * @param templatesDir    optional filesystem override for the classpath templates
 *                        ({@code infra/templates/} in prod — empty = classpath)
 */
@ConfigurationProperties(prefix = "forgesys.mail")
public record MailProperties(String from, String defaultLanguage, String templatesDir) {

    public String effectiveFrom() {
        return (from == null || from.isBlank()) ? "ForgeSys <no-reply@forgessy.local>" : from;
    }

    public String effectiveLanguage() {
        return (defaultLanguage == null || defaultLanguage.isBlank()) ? "tr" : defaultLanguage;
    }
}
