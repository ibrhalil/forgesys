package com.ibrhalil.forgesys.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** {@code dev}-profile {@link MailSender} (unless the {@code smtp} companion profile is stacked — K-53): logs the message incl. the raw-token URL (dev-only convenience). */
@Slf4j
@Component
@Profile("dev & !smtp")
public class LogMailSender implements MailSender {

    @Override
    public MailChannel channel() {
        return MailChannel.LOG;
    }

    @Override
    public void send(MailMessage message) {
        log.info("[DEV MAIL] to='{}' template={} actionUrl={}",
                message.recipient(), message.template(), message.actionUrl());
    }
}
