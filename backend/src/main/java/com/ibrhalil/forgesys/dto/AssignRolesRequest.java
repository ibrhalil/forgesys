package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Replace-semantics role assignment ({@code PUT /users/{id}/roles} or
 * {@code PUT /groups/{id}/roles}); empty clears, unresolvable ids 404.
 */
public record AssignRolesRequest(
        @NotNull(message = "roleIds must be present (use an empty list to clear)")
        @Size(max = 100, message = "At most 100 role ids per request")
        List<UUID> roleIds
) {
}
