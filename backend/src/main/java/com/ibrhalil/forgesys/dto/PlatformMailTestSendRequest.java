package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.service.mail.MailTemplate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * K-51 test mail: render + hand to the active {@code MailSender}. Fail-loud on
 * delivery errors (K-48) — the response echoes the channel so the caller knows what
 * "sent" means in this profile (SMTP vs LOG vs IN_MEMORY).
 */
public record PlatformMailTestSendRequest(
        @NotBlank @Email String recipient,
        @NotNull MailTemplate template,
        @Pattern(regexp = "(?i)tr|en") String language,
        String firstName,
        String organizationName,
        String actionUrl,
        @Min(1) Integer expiresInHours) {
}
