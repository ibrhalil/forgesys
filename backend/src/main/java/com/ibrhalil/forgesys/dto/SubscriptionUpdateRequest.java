package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * K-50 F4: platform-driven plan change ({@code PUT /platform/companies/{id}/subscription});
 * the key is validated against the {@code PlanDefinition} registry.
 */
public record SubscriptionUpdateRequest(
        @NotBlank(message = "Plan key is required")
        String planKey) {
}
