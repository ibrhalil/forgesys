package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Last-admin invariant: at least one enabled, non-deleted admin-capable user must
 * survive every mutation — no in-product recovery exists, so prevention is the only
 * defense. Admin-capable = effective role closure holds an {@code all_permissions} role
 * (NOT the role name "Admin", NOT a specific permission), expanded DOWNWARD through
 * child roles. Check after the mutation but inside the same tx (JPQL auto-flushes
 * pending changes; violation → 409 rollback). For deletes, resolve bearers BEFORE the
 * soft-delete ({@code @SQLRestriction} hides deleted rows from JPQL).
 * rationale: docs/CODE_NOTES.md (backend/security → LastAdminGuard)
 */
@Component
@RequiredArgsConstructor
public class LastAdminGuard {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    /**
     * Rejects the actor deleting/disabling their own account — unconditionally, even
     * when other admins exist (self-delete was the direct cause of tenant lockouts).
     */
    public void assertNotSelf(UUID targetUserId) {
        UUID actorId = currentUserId();
        if (actorId != null && actorId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.SELF_DELETE_FORBIDDEN);
        }
    }

    /** Post-mutation check: throws {@code LAST_ADMIN_REQUIRED} (409) when no enabled admin-capable user remains. */
    public void assertActiveAdminExists() {
        Set<UUID> adminRoleIds = adminCapableRoleIds();
        if (!adminRoleIds.isEmpty() && userRepository.existsEnabledByRoleIds(adminRoleIds)) {
            return;
        }
        throw new BusinessException(ErrorCode.LAST_ADMIN_REQUIRED);
    }

    /**
     * All-permissions roles plus their transitive children ({@code t_role_parents} walked
     * to a fixpoint — acyclic by {@code RoleService.setParents}); soft-deleted roles are
     * filtered by {@code @SQLRestriction}. K-50: also the impersonation target's
     * admin-capability definition (PlatformSwitchService reuses this closure).
     */
    public Set<UUID> adminCapableRoleIds() {
        Set<UUID> roleIds = new LinkedHashSet<>();
        for (Role role : roleRepository.findAllByAllPermissionsTrue()) {
            if (role.getId() != null) {
                roleIds.add(role.getId());
            }
        }
        // Downward closure: children of admin roles inherit everything and
        // short-circuit to ALL in resolvePermissionNames.
        Deque<UUID> frontier = new ArrayDeque<>(roleIds);
        while (!frontier.isEmpty()) {
            List<UUID> batch = new java.util.ArrayList<>();
            while (!frontier.isEmpty()) {
                batch.add(frontier.poll());
            }
            for (UUID childId : roleRepository.findChildRoleIds(batch)) {
                if (roleIds.add(childId)) {
                    frontier.add(childId);
                }
            }
        }
        return roleIds;
    }

    /** Authenticated principal's user id, or {@code null} when unauthenticated/system. */
    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }
}
