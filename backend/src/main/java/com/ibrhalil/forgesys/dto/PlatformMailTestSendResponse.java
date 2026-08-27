package com.ibrhalil.forgesys.dto;

/** K-51 test-send result — the channel that handled the mail plus the resolved request echo. */
public record PlatformMailTestSendResponse(
        String channel,
        String recipient,
        String template,
        String language) {
}
