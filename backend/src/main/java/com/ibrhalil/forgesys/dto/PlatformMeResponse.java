package com.ibrhalil.forgesys.dto;

import java.util.List;
import java.util.UUID;

/** K-50 platform identity self view (backs the platform console bootstrap). */
public record PlatformMeResponse(
        UUID userId,
        String email,
        String displayName,
        String userType,
        List<String> authorities
) {
}
