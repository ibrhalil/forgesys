package com.ibrhalil.forgesys.service.mail;

/**
 * Outgoing mail port (user lifecycle + mail epic). Profile-driven implementations:
 * {@code SmtpMailSender} for {@code prod} ({@code spring-boot-starter-mail}), {@code
 * LogMailSender} for {@code dev} (no SMTP dependency locally), {@code InMemoryMailSender}
 * for {@code test} (collects messages for assertions). Replaces the former K-21
 * {@code VerificationSender} (URL-only) — every message is template-based so the same
 * channel carries tenant signup, email verification and password reset mails.
 */
public interface MailSender {

    /**
     * Renders and delivers the message. Implementations may throw on delivery failure
     * (fail-loud — a silently lost signup/reset link is worse than a retryable error).
     */
    void send(MailMessage message);
}
