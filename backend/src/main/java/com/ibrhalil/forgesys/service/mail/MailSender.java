package com.ibrhalil.forgesys.service.mail;

/**
 * Outgoing mail port; profile-split implementations: {@code SmtpMailSender} (prod),
 * {@code LogMailSender} (dev), {@code InMemoryMailSender} (test). Replaced the former
 * K-21 {@code VerificationSender} — every message is template-based.
 * Rationale: docs/CODE_NOTES.md (backend/service → mail).
 */
public interface MailSender {

    /**
     * May throw on delivery failure — fail-loud: a silently lost signup/reset link is
     * worse than a retryable error.
     */
    void send(MailMessage message);

    /** Delivery channel of this implementation (K-51 mail-test info surface). */
    MailChannel channel();
}
