package com.ibrhalil.forgesys.service;

/**
 * Sends a tenant-signup verification link to the admin email address (K-21). The link
 * points at {@code POST /api/v1/auth/company/verify} and carries the token produced by
 * {@code TenantProvisioningService.createPendingCompany}.
 *
 * <p>Profile-driven implementations: {@code LogVerificationSender} for {@code dev}/
 * {@code prod} (logs the link; the real {@code MailVerificationSender} arrives in Faz 5
 * with {@code spring-boot-starter-mail}), {@code InMemoryVerificationSender} for
 * {@code test} (collects links so tests can assert). The caller is responsible for
 * building the URL — the sender only delivers it.
 */
public interface VerificationSender {

    void send(String emailAddress, String verificationUrl);
}
