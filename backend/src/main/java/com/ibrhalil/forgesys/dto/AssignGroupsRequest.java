package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Replace-semantics request for assigning groups to a user ({@code PUT /users/{id}/groups}).
 * An empty list clears membership. Group ids that do not resolve are rejected with 404.
 */
public record AssignGroupsRequest(
        @NotNull(message = "groupIds must be present (use an empty list to clear)")
        List<UUID> groupIds
) {
}
