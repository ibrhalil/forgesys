package com.ibrhalil.systemforge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 150)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 255, message = "Password must be 8-255 characters")
        String password,

        @Size(max = 70, message = "Username must be at most 70 characters")
        String username,

        @Size(max = 100) String firstName,

        @Size(max = 100) String lastName,

        Boolean enabled
) {
}
