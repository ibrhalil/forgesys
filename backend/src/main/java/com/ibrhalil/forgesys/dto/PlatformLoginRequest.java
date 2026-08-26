package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** K-50 platform superadmin login — HUMAN identities only (SERVICE uses X-API-Key). */
public record PlatformLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
