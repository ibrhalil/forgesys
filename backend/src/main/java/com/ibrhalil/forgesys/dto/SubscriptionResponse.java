package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * K-50 F4: a tenant's subscription view for the platform console
 * ({@code GET/PUT /platform/companies/{id}/subscription}).
 */
public record SubscriptionResponse(
        UUID companyId,
        String planKey,
        String planName,
        String status,
        OffsetDateTime startedAt) {
}
