package com.ibrhalil.forgesys.dto;

import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        boolean allPermissions,
        List<PermissionResponse> permissions,
        List<RoleSummary> parents
) {
}
