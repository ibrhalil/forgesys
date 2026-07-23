package com.ibrhalil.systemforge.dto;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        boolean emailVerified,
        boolean enabled,
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
