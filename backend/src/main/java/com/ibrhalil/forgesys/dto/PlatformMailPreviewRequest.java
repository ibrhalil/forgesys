package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.service.mail.MailTemplate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * K-51 mail-test preview: render a template with sample data WITHOUT sending.
 * Blank sample fields fall back to service defaults.
 *
 * @param template         which template to render (enum name)
 * @param language         tr|en (nullable — configured default)
 * @param firstName        sample recipient name
 * @param organizationName sample organization name
 * @param actionUrl        sample action link
 * @param expiresInHours   sample link validity
 */
public record PlatformMailPreviewRequest(
        @NotNull MailTemplate template,
        @Pattern(regexp = "(?i)tr|en") String language,
        String firstName,
        String organizationName,
        String actionUrl,
        Integer expiresInHours) {
}
