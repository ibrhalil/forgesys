package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.PlatformMailInfoResponse;
import com.ibrhalil.forgesys.dto.PlatformMailPreviewRequest;
import com.ibrhalil.forgesys.dto.PlatformMailPreviewResponse;
import com.ibrhalil.forgesys.dto.PlatformMailTemplateInfo;
import com.ibrhalil.forgesys.dto.PlatformMailTestSendRequest;
import com.ibrhalil.forgesys.dto.PlatformMailTestSendResponse;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.service.mail.MailMessage;
import com.ibrhalil.forgesys.service.mail.MailProperties;
import com.ibrhalil.forgesys.service.mail.MailSender;
import com.ibrhalil.forgesys.service.mail.MailTemplate;
import com.ibrhalil.forgesys.service.mail.MailTemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * K-51 platform mail-test surface: config info, no-send template preview and a real
 * test send through the active {@link MailSender}. No DB writes besides the
 * best-effort platform audit entry on send.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformMailTestService {

    public static final String ACTION_MAIL_TEST_SENT = "platform_mail_test_sent";

    /** Preview never delivers — a syntactically valid, clearly fake recipient. */
    static final String PREVIEW_RECIPIENT = "preview@mail-test.invalid";

    static final String DEFAULT_FIRST_NAME = "Test";
    static final String DEFAULT_ORGANIZATION_NAME = "ForgeSys Test";
    static final String DEFAULT_ACTION_URL = "https://mail-test.invalid/verify?token=mail-test-token";
    static final int DEFAULT_EXPIRES_IN_HOURS = 24;

    private final MailSender mailSender;
    private final MailTemplateRenderer renderer;
    private final MailProperties mailProperties;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformAuditService platformAuditService;

    public PlatformMailInfoResponse info() {
        List<PlatformMailTemplateInfo> templates = Arrays.stream(MailTemplate.values())
                .map(template -> new PlatformMailTemplateInfo(
                        template.name(), template.key(), template.subject("tr"), template.subject("en")))
                .toList();
        return new PlatformMailInfoResponse(mailSender.channel().name(), mailProperties.effectiveFrom(),
                mailProperties.effectiveLanguage(),
                mailProperties.templatesDir() == null ? "" : mailProperties.templatesDir(),
                templates);
    }

    public PlatformMailPreviewResponse preview(PlatformMailPreviewRequest request) {
        MailTemplateRenderer.RenderedMail rendered = renderer.render(
                toMessage(PREVIEW_RECIPIENT, request.template(), request.language(), request.firstName(),
                        request.organizationName(), request.actionUrl(), request.expiresInHours()));
        return new PlatformMailPreviewResponse(rendered.subject(), rendered.bodyHtml());
    }

    public PlatformMailTestSendResponse testSend(PlatformMailTestSendRequest request) {
        String language = resolvedLanguage(request.language());
        MailMessage message = toMessage(request.recipient(), request.template(), language, request.firstName(),
                request.organizationName(), request.actionUrl(), request.expiresInHours());
        mailSender.send(message);
        auditSent(request, language);
        return new PlatformMailTestSendResponse(mailSender.channel().name(),
                request.recipient(), request.template().name(), language);
    }

    private MailMessage toMessage(String recipient, MailTemplate template, String language, String firstName,
                                  String organizationName, String actionUrl, Integer expiresInHours) {
        Duration expiresIn = Duration.ofHours(expiresInHours == null ? DEFAULT_EXPIRES_IN_HOURS : expiresInHours);
        return new MailMessage(recipient, template,
                actionUrl == null || actionUrl.isBlank() ? DEFAULT_ACTION_URL : actionUrl,
                firstName == null || firstName.isBlank() ? DEFAULT_FIRST_NAME : firstName,
                organizationName == null || organizationName.isBlank() ? DEFAULT_ORGANIZATION_NAME : organizationName,
                expiresIn,
                language);
    }

    private String resolvedLanguage(String language) {
        return language == null || language.isBlank()
                ? mailProperties.effectiveLanguage()
                : language.toLowerCase(Locale.ROOT);
    }

    /** Best-effort platform audit entry (actor = platform principal; SYSTEM fallback). */
    private void auditSent(PlatformMailTestSendRequest request, String language) {
        try {
            UUID actorId = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails principal) {
                actorId = principal.getUserId();
            }
            String actorType = actorId == null ? PlatformAuditService.ACTOR_SYSTEM
                    : platformUserRepository.findById(actorId)
                            .map(user -> user.getUserType().name())
                            .orElse(PlatformAuditService.ACTOR_SYSTEM);
            platformAuditService.record(actorId, actorType, ACTION_MAIL_TEST_SENT, "MailTemplate", null,
                    "template=%s language=%s".formatted(request.template().name(), language));
        } catch (RuntimeException ex) {
            log.warn("Failed to record platform audit entry (action={})", ACTION_MAIL_TEST_SENT, ex);
        }
    }
}
