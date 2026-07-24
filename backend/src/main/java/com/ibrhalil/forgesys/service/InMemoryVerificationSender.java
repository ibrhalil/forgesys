package com.ibrhalil.forgesys.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code test}-profile {@link VerificationSender} (K-21). Collects every delivered link
 * in-process so tests can assert the signup → verify flow without a mail server. Thread
 * -safe ({@link CopyOnWriteArrayList}); cleared per-test by {@code @SpringBootTest}
 * context restart or explicitly via {@link #clear()}.
 */
@Component
@Profile("test")
public class InMemoryVerificationSender implements VerificationSender {

    private final List<DeliveredLink> delivered = new CopyOnWriteArrayList<>();

    @Override
    public void send(String emailAddress, String verificationUrl) {
        delivered.add(new DeliveredLink(emailAddress, verificationUrl));
    }

    public List<DeliveredLink> getDelivered() {
        return List.copyOf(delivered);
    }

    public void clear() {
        delivered.clear();
    }

    public record DeliveredLink(String emailAddress, String verificationUrl) {
    }
}
