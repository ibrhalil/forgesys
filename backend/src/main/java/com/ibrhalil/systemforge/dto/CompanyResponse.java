package com.ibrhalil.systemforge.dto;

import com.ibrhalil.systemforge.entity.CompanyStatus;

import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        String subdomain,
        String emailDomain,
        String schemaName,
        String dbRole,
        CompanyStatus status
) {
}
