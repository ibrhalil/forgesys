package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        boolean emailVerified,
        boolean enabled,
        OffsetDateTime lockedUntil,
        String firstName,
        String lastName,
        String phoneNumber,
        String address,
        String city,
        String country,
        String zipCode,
        List<RoleSummary> roles,
        List<GroupSummary> groups
) {
}
