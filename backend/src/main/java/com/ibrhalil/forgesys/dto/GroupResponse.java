package com.ibrhalil.forgesys.dto;

import java.util.List;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        List<RoleSummary> roles,
        long memberCount
) {
}
