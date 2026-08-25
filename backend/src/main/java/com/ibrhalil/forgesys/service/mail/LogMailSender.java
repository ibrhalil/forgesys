package com.ibrhalil.forgesys.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code dev}-profile {@link MailSender} — no SMTP dependency locally; logs the message
 * (including the action URL, which carries the raw token — dev-only convenience, the
 * same trade-off the former K-21 {@code LogVerificationSender} made). Prod uses
 * {@code SmtpMailSender}.
 */
@Slf4j
@Component
@Profile("dev")
public class LogMailSender implements MailSender {

    @Override
    public void send(MailMessage message) {
        log.info("[DEV MAIL] to='{}' template={} actionUrl={}",
                message.recipient(), message.template(), message.actionUrl());
    }
}
