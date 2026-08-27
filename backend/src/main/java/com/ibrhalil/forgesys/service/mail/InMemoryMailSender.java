package com.ibrhalil.forgesys.service.mail;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** {@code test}-profile {@link MailSender}: collects delivered messages in-process for assertions. */
@Component
@Profile("test")
public class InMemoryMailSender implements MailSender {

    private final List<MailMessage> delivered = new CopyOnWriteArrayList<>();

    @Override
    public MailChannel channel() {
        return MailChannel.IN_MEMORY;
    }

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
