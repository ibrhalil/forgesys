package com.ibrhalil.forgesys.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * {@link MailSender} for the {@code prod} profile and the opt-in {@code smtp}
 * companion profile ({@code dev,smtp} — K-53) — real SMTP via
 * {@code JavaMailSender} (provider-agnostic: anything that speaks
 * {@code spring.mail.*}). Fail-fast at startup when {@code spring.mail.host}
 * is unset, fail-loud on send errors (the caller's tx rolls back — a silently
 * lost signup/reset link is far worse). Recipients are intentionally NOT
 * logged (PII).
 */
@Slf4j
@Component
@Profile("prod | smtp")
public class SmtpMailSender implements MailSender {

    private final JavaMailSender javaMailSender;
    private final MailTemplateRenderer renderer;
    private final MailProperties properties;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public SmtpMailSender(JavaMailSender javaMailSender, MailTemplateRenderer renderer, MailProperties properties) {
        this.javaMailSender = javaMailSender;
        this.renderer = renderer;
        this.properties = properties;
    }

    @PostConstruct
    void verifyConfigured() {
        if (mailHost == null || mailHost.isBlank()) {
            throw new IllegalStateException(
                    "SmtpMailSender (prod) requires spring.mail.host — set the MAIL_HOST env var");
        }
    }

    @Override
    public MailChannel channel() {
        return MailChannel.SMTP;
    }

    @Override
    public void send(MailMessage message) {
        MailTemplateRenderer.RenderedMail rendered = renderer.render(message);
        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(properties.effectiveFrom());
            helper.setTo(message.recipient());
            helper.setSubject(rendered.subject());
            helper.setText(rendered.bodyHtml(), true);
            javaMailSender.send(mime);
            log.info("Mail sent: template={}", message.template());
        } catch (MessagingException | MailException e) {
            log.error("Mail send failed: template={}", message.template(), e);
            throw new IllegalStateException("Mail send failed for template " + message.template(), e);
        }
    }
}
