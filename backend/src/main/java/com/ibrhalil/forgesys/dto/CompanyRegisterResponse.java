package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.CompanyStatus;

import java.util.UUID;

/**
 * Response of {@code POST /auth/company/register} (K-21): Company is
 * {@code PROVISIONING}; verification via the mailed link is required before the
 * tenant becomes usable.
 */
public record CompanyRegisterResponse(
        UUID companyId,
        String name,
        String subdomain,
        CompanyStatus status,
        String message
) {
}
