package com.ibrhalil.forgesys.service.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * K-53 sender selection: {@code dev} → Log, {@code dev,smtp} / {@code prod} → Smtp,
 * {@code test} → InMemory. The {@code smtp} companion profile opts dev into real SMTP
 * (Mailpit by default); LogMailSender must step aside when it is stacked.
 */
class MailSenderProfileSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withBean(MailTemplateRenderer.class, () -> mock(MailTemplateRenderer.class))
            .withBean(MailProperties.class, () -> new MailProperties("Test <t@example.com>", "tr", null))
            .withUserConfiguration(SmtpMailSender.class, LogMailSender.class, InMemoryMailSender.class);

    @Test
    void devActivatesLogSender() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(LogMailSender.class);
                    assertThat(ctx).doesNotHaveBean(SmtpMailSender.class);
                    assertThat(ctx).doesNotHaveBean(InMemoryMailSender.class);
                });
    }

    @Test
    void devWithSmtpCompanionActivatesSmtpSenderInsteadOfLog() {
        runner.withPropertyValues("spring.profiles.active=dev,smtp", "spring.mail.host=localhost")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SmtpMailSender.class);
                    assertThat(ctx).doesNotHaveBean(LogMailSender.class);
                    assertThat(ctx).doesNotHaveBean(InMemoryMailSender.class);
                });
    }

    @Test
    void prodActivatesSmtpSender() {
        runner.withPropertyValues("spring.profiles.active=prod", "spring.mail.host=relay.example.com")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SmtpMailSender.class);
                    assertThat(ctx).doesNotHaveBean(LogMailSender.class);
                });
    }

    @Test
    void testActivatesInMemorySender() {
        runner.withPropertyValues("spring.profiles.active=test")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(InMemoryMailSender.class);
                    assertThat(ctx).doesNotHaveBean(SmtpMailSender.class);
                    assertThat(ctx).doesNotHaveBean(LogMailSender.class);
                });
    }
}
