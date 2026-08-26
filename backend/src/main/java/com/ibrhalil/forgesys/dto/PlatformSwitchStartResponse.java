package com.ibrhalil.forgesys.dto;

/** K-50 F6: switch start result — the raw code (shown once) + the tenant tab URL. */
public record PlatformSwitchStartResponse(
        String switchCode,
        String targetUrl
) {
}
