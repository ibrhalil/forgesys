package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Request body for replacing a role's permission set ({@code PUT /roles/{id}/permissions}).
 *
 * <p>Two modes:
 * <ul>
 *   <li><strong>Explicit ({@code all} null/false):</strong> replace semantics — the role's
 *       permissions are set to exactly {@link #permissionIds} (an empty list clears all).
 *       Ids that do not resolve are rejected with 404.</li>
 *   <li><strong>All ({@code all=true}):</strong> set the role's {@code all_permissions}
 *       flag so it implicitly holds every permission in the tenant (resolved dynamically).
 *       {@link #permissionIds} is ignored; the explicit set is cleared. This is the
 *       "ALL" shortcut so an admin need not select every permission by hand.</li>
 * </ul>
 * Switching back to explicit mode ({@code all=false}) clears the flag and applies
 * {@link #permissionIds}, which must then be present.
 */
public record AssignPermissionsRequest(
        @Size(max = 100, message = "At most 100 permission ids per request")
        List<UUID> permissionIds,
        Boolean all
) {
}
