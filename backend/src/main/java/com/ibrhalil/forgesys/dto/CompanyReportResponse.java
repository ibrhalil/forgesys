package com.ibrhalil.forgesys.dto;

import java.util.UUID;

/**
 * K-50 F4: cross-tenant usage counters for one company
 * ({@code GET /platform/companies/{id}/report}) — count queries only, no
 * entity hydration.
 */
public record CompanyReportResponse(
        UUID companyId,
        long userCount,
        long projectCount,
        long appCount,
        long noteCount) {
}
