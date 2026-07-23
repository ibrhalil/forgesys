package com.ibrhalil.systemforge.dto;

import jakarta.validation.constraints.Size;

/**
 * Self-service profile update ({@code PUT /api/v1/users/me/profile}). All fields are
 * optional; only the supplied fields are applied (null = leave unchanged).
 */
public record UserProfileUpdateRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 50) String phoneNumber,
        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String country,
        @Size(max = 20) String zipCode
) {
}
