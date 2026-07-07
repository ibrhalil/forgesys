package com.ibrhalil.systemforge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRegisterRequest(
        @NotBlank(message = "Company name is required")
        @Size(max = 150, message = "Company name must be at most 150 characters")
        String companyName,

        @NotBlank(message = "Subdomain is required")
        @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?$",
                message = "Subdomain must be 1-100 chars: lowercase alphanumeric and hyphens, not starting/ending with hyphen")
        String subdomain,

        @NotBlank(message = "Email domain is required")
        @Pattern(regexp = "^[a-z0-9]([a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,}$",
                message = "Invalid email domain format")
        @Size(max = 150)
        String emailDomain,

        @NotBlank(message = "Admin email is required")
        @Email(message = "Invalid admin email format")
        @Size(max = 150)
        String adminEmail,

        @NotBlank(message = "Admin password is required")
        @Size(min = 8, max = 255, message = "Password must be 8-255 characters")
        String adminPassword,

        @Size(max = 100) String adminFirstName,
        @Size(max = 100) String adminLastName
) {
}
