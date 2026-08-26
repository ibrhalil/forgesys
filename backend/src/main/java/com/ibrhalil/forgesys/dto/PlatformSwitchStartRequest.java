package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;

/** K-50 F6: switch start — the mandatory audit reason. */
public record PlatformSwitchStartRequest(
        @NotBlank String reason
) {
}
