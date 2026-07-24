package com.ibrhalil.forgesys.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code dev}/{@code prod} {@link VerificationSender} (K-21). Logs the verification link
 * at INFO so a developer can click it during signup testing. The real
 * {@code MailVerificationSender} (Faz 5, {@code spring-boot-starter-mail}) replaces this
 * for prod once mail infra lands — until then prod signups must be completed via the
 * logged link. Active for every non-{@code test} profile.
 */
@Slf4j
@Component
@Profile("!test")
public class LogVerificationSender implements VerificationSender {

    @Override
    public void send(String emailAddress, String verificationUrl) {
        log.info("Tenant signup verification link for {} -> {}", emailAddress, verificationUrl);
    }
}
