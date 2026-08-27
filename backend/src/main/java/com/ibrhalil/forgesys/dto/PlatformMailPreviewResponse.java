package com.ibrhalil.forgesys.dto;

/** K-51 mail-test render result — the exact subject + HTML body a send would deliver. */
public record PlatformMailPreviewResponse(
        String subject,
        String bodyHtml) {
}
