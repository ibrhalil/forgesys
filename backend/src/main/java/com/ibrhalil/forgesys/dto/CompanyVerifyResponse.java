package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.CompanyStatus;

import java.util.UUID;

/**
 * Response of {@code POST /api/v1/auth/company/verify} (K-21). On success the Company
 * is {@code ACTIVE}, the tenant schema + Flyway migration + admin user are in place, and
 * the caller can log in via the tenant subdomain.
 */
public record CompanyVerifyResponse(
        UUID companyId,
        String name,
        String subdomain,
        CompanyStatus status,
        String message
) {
}
