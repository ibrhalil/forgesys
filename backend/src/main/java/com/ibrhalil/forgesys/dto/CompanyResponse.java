package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.CompanyStatus;

import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        String subdomain,
        CompanyStatus status
) {
}
