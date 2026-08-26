package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Replace-semantics member assignment ({@code PUT /groups/{id}/members}); empty
 * removes all, unresolvable ids 404.
 */
public record AssignMembersRequest(
        @NotNull(message = "userIds must be present (use an empty list to clear)")
        @Size(max = 100, message = "At most 100 user ids per request")
        List<UUID> userIds
) {
}
