package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/company/verify} (K-21). The token was issued
 * by {@code register} and delivered to the admin email by
 * {@link com.ibrhalil.forgesys.service.mail.MailSender}.
 */
public record CompanyVerifyRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}
