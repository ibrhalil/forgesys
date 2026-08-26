package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/** K-50 service-account creation — scopes must exist in {@code PlatformPermissionCatalog}. */
public record PlatformServiceAccountCreateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotEmpty List<String> scopes,
        OffsetDateTime expiresAt
) {
}
