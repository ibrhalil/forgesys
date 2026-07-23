package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for replacing a role's permission set ({@code PUT /roles/{id}/permissions}).
 * Replace semantics: the role's permissions are set to exactly this list (an empty list
 * clears all permissions). Permission ids that do not resolve are rejected with 404.
 */
public record AssignPermissionsRequest(
        @NotNull(message = "permissionIds must be present (use an empty list to clear)")
        List<UUID> permissionIds
) {
}
