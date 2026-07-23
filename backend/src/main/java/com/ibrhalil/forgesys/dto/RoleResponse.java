package com.ibrhalil.forgesys.dto;

import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        java.util.List<PermissionResponse> permissions
) {
}
