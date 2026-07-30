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
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Last-admin invariant (self-delete guard + active-admin floor).
 *
 * <p>Root cause this closes: a tenant admin could soft-delete <em>themselves</em> (or
 * disable themselves, or strip their own admin role, or delete/degrade the last
 * admin-capable role or its group), leaving the tenant with zero admin-capable users —
 * nobody can manage anything. No recovery path exists in-product (platform-level rescue
 * is deliberate future work), so prevention is the only defense.
 *
 * <p><strong>Admin-capable definition</strong> (mirrors
 * {@code CustomUserDetailsService} authority resolution, direction reversed): a user is
 * admin-capable when their effective role closure (direct roles + roles of active
 * groups + transitive parent inheritance) contains at least one role with
 * {@code all_permissions=true}. It is NOT the role <em>name</em> "Admin" (that is only a
 * seed convention) and NOT a specific permission. A role that inherits from an
 * all-permissions role is itself all-permissions, so the guard expands the seeded flag
 * roles <em>downward</em> through {@code t_role_parents} (children of an admin role are
 * admin-capable too) before checking for holders.
 *
 * <p><strong>Invariant:</strong> at least one non-deleted AND {@code enabled=true}
 * admin-capable user must remain after every mutation ({@code @SQLRestriction} hides
 * soft-deleted users; disabled admins don't count).
 *
 * <p>Usage: call <em>after</em> the mutation but <em>inside</em> the same transaction —
 * the existence query is JPQL, so Hibernate auto-flushes the pending entity changes
 * (removed role/group join rows, {@code enabled=false}, soft-delete UPDATE) before it
 * runs, and a violation throws {@link ErrorCode#LAST_ADMIN_REQUIRED} (409) to roll the
 * whole mutation back. For deletes, resolve holder ids / run {@code existsEnabled*}
 * style checks BEFORE the soft-delete — {@code @SQLRestriction} makes deleted rows
 * invisible to JPQL afterwards.
 */
@Component
@RequiredArgsConstructor
public class LastAdminGuard {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    /**
     * Rejects the actor deleting/disabling their own account. Self-delete is forbidden
     * unconditionally — even when other admins exist — because it is never necessary
     * and historically the direct cause of tenant lockouts. Actor comes from the
     * SecurityContext; throws {@link ErrorCode#SELF_DELETE_FORBIDDEN} (409) when
     * {@code targetUserId} matches the authenticated principal.
     */
    public void assertNotSelf(UUID targetUserId) {
        UUID actorId = currentUserId();
        if (actorId != null && actorId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.SELF_DELETE_FORBIDDEN);
        }
    }

    /**
     * Post-mutation invariant check: at least one enabled admin-capable user must
     * remain. Runs inside the caller's transaction — pending mutations are auto-flushed
     * before the query. Throws {@link ErrorCode#LAST_ADMIN_REQUIRED} (409) on violation,
     * rolling the mutation back.
     */
    public void assertActiveAdminExists() {
        Set<UUID> adminRoleIds = adminCapableRoleIds();
        if (!adminRoleIds.isEmpty() && userRepository.existsEnabledByRoleIds(adminRoleIds)) {
            return;
        }
        throw new BusinessException(ErrorCode.LAST_ADMIN_REQUIRED);
    }

    /**
     * Admin-capable role ids of the tenant: every role carrying the
     * {@code all_permissions} flag plus, transitively, every role that inherits from
     * one ({@code t_role_parents} walked downward to a fixpoint — inheritance is
     * acyclic by {@code RoleService.setParents}). Soft-deleted roles are filtered by
     * {@code @SQLRestriction} on both queries.
     */
    private Set<UUID> adminCapableRoleIds() {
        Set<UUID> roleIds = new LinkedHashSet<>();
        for (Role role : roleRepository.findAllByAllPermissionsTrue()) {
            if (role.getId() != null) {
                roleIds.add(role.getId());
            }
        }
        // Downward closure: children of admin roles are admin-capable too (a child
        // inherits everything its parents grant — resolvePermissionNames short-circuits
        // to ALL when any closure member carries the flag).
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
