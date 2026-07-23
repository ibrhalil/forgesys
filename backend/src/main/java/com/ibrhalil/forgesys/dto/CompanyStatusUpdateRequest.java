package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.CompanyStatus;
import jakarta.validation.constraints.NotNull;

public record CompanyStatusUpdateRequest(
        @NotNull(message = "Status cannot be null")
        CompanyStatus status
) {
}
