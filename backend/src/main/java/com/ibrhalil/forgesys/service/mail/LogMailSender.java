package com.ibrhalil.forgesys.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** {@code dev}-profile {@link MailSender}: logs the message incl. the raw-token URL (dev-only convenience). */
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
