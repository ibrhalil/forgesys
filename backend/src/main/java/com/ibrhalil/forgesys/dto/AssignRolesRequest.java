package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Replace-semantics request for assigning roles to a user ({@code PUT /users/{id}/roles})
 * or to a group ({@code PUT /groups/{id}/roles}). An empty list clears the assignment.
 * Role ids that do not resolve are rejected with 404.
 */
public record AssignRolesRequest(
        @NotNull(message = "roleIds must be present (use an empty list to clear)")
        List<UUID> roleIds
) {
}
