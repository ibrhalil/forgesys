package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Replaces a role's permission set ({@code PUT /roles/{id}/permissions}), two
 * modes: explicit ({@code all} null/false — set exactly {@link #permissionIds},
 * empty clears all, unresolvable ids 404) or {@code all=true} (set the
 * {@code all_permissions} flag, ignore {@link #permissionIds}). Switching back
 * to explicit clears the flag; {@link #permissionIds} must then be present.
 */
public record AssignPermissionsRequest(
        @Size(max = 100, message = "At most 100 permission ids per request")
        List<UUID> permissionIds,
        Boolean all
) {
}
