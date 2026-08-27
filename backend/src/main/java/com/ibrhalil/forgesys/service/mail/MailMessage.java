package com.ibrhalil.forgesys.service.mail;

import java.time.Duration;

/**
 * A single outgoing mail. The action URL token is RAW and must never be persisted by
 * the sender ([RISK-30]).
 *
 * @param recipient        admin/user email address
 * @param template         which {@link MailTemplate} to render
 * @param actionUrl        the link the recipient should click
 * @param firstName        recipient display name (nullable — template falls back)
 * @param organizationName tenant/company display name (nullable)
 * @param expiresIn        link validity (nullable) — rendered as hours/minutes
 * @param language         template language override (nullable — configured default)
 */
public record MailMessage(
        String recipient,
        MailTemplate template,
        String actionUrl,
        String firstName,
        String organizationName,
        Duration expiresIn,
        String language) {

    /** Pre-K-51 shape — default language applies. */
    public MailMessage(String recipient, MailTemplate template, String actionUrl,
                       String firstName, String organizationName, Duration expiresIn) {
        this(recipient, template, actionUrl, firstName, organizationName, expiresIn, null);
    }
}
