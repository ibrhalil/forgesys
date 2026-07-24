package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.CompanyStatus;

import java.util.UUID;

/**
 * Response of {@code POST /api/v1/auth/company/register} (K-21). The Company is created
 * in {@code PROVISIONING} state; the schema and admin user do not exist yet. The caller
 * must verify via the link sent to the admin email before the tenant becomes usable.
 */
public record CompanyRegisterResponse(
        UUID companyId,
        String name,
        String subdomain,
        CompanyStatus status,
        String message
) {
}
