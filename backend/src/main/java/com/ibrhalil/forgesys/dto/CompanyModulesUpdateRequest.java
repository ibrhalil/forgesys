package com.ibrhalil.forgesys.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * K-50 F4: platform-driven module activation set
 * ({@code PUT /platform/companies/{id}/modules}) — each entry activates or
 * deactivates one module key; unknown keys 404.
 */
public record CompanyModulesUpdateRequest(
        @NotEmpty(message = "At least one module activation is required")
        @Valid
        List<ModuleActivation> activations) {

    public record ModuleActivation(
            @NotBlank(message = "Module key is required")
            String key,
            boolean active) {
    }
}
