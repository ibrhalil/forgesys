package com.ibrhalil.systemforge.dto;

import com.ibrhalil.systemforge.entity.CompanyStatus;
import jakarta.validation.constraints.NotNull;

public record CompanyStatusUpdateRequest(
        @NotNull(message = "Status cannot be null")
        CompanyStatus status
) {
}
