package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.CompanyStatus;

import java.util.UUID;

/**
 * Response of {@code POST /auth/company/verify} (K-21): the Company is
 * {@code ACTIVE} and the caller can log in via the tenant subdomain.
 */
public record CompanyVerifyResponse(
        UUID companyId,
        String name,
        String subdomain,
        CompanyStatus status,
        String message
) {
}
