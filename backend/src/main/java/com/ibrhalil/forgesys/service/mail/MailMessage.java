package com.ibrhalil.forgesys.service.mail;

import java.time.Duration;

/**
 * A single outgoing mail: recipient, template and the placeholders the template needs.
 * The action URL (verification/reset link) is passed by the caller — the token inside
 * it is raw and must never be persisted by the sender.
 *
 * @param recipient         admin/user email address
 * @param template          which {@link MailTemplate} to render
 * @param actionUrl         the link the recipient should click
 * @param firstName         recipient display name (nullable — template falls back to a
 *                          generic greeting)
 * @param organizationName  tenant/company display name (nullable)
 * @param expiresIn         link validity (nullable) — templates render it as
 *                          {@code {{expiresInHours}}} or {@code {{expiresInMinutes}}}
 */
public record MailMessage(
        String recipient,
        MailTemplate template,
        String actionUrl,
        String firstName,
        String organizationName,
        Duration expiresIn) {
}
