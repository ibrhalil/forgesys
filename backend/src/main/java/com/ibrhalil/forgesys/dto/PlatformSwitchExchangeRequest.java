package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;

/** K-50 F6: one-time switch-code exchange on the target tenant's host. */
public record PlatformSwitchExchangeRequest(
        @NotBlank String code
) {
}
