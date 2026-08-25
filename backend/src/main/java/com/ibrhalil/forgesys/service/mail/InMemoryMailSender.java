package com.ibrhalil.forgesys.service.mail;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@code test}-profile {@link MailSender}. Collects every delivered message in-process
 * so tests can assert mail-driven flows without a mail server. Thread-safe
 * ({@link CopyOnWriteArrayList}); cleared per-test by context restart or explicitly
 * via {@link #clear()}.
 */
@Component
@Profile("test")
public class InMemoryMailSender implements MailSender {

    private final List<MailMessage> delivered = new CopyOnWriteArrayList<>();

    @Override
    public void send(MailMessage message) {
        delivered.add(message);
    }

    public List<MailMessage> getDelivered() {
        return List.copyOf(delivered);
    }

    public void clear() {
        delivered.clear();
    }
}
