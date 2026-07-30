package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Loads a user (by email, within the current tenant schema resolved by
 * {@code TenantFilter}) and resolves its effective authorities:
 * <strong>direct user roles</strong> + <strong>active group roles</strong>, each
 * expanded to their permissions ({@code {module}:{resource}:{action}}) <em>and</em> the
 * permissions of every transitively inherited parent role (Faz 4a role inheritance).
 *
 * <p>{@link #loadUserByUsername(String)} is the {@link UserDetailsService} contract.
 * {@link #resolveAuthorities(UUID)} is shared authority resolution invoked by
 * {@code AuthService} at login. The JWT filter reconstructs the principal from claims
 * on subsequent requests (no DB load), so permission changes apply on the next token.
 *
 * <p><strong>DB-driven resolution:</strong> authorities are resolved with explicit
 * {@link UserRepository} queries (direct + active-group role ids, transitive parent
 * closure, permission names) rather than by traversing lazy entity collections. The
 * previous entity-graph walk depended on nested lazy collections
 * ({@code group.roles} -> {@code role.permissions} -> {@code role.parentRoles})
 * initializing reliably through {@code findByEmail} (no fetch graph), which is fragile
 * and an N+1 source; a full fetch graph is impossible here (Hibernate multiple-bags
 * limit). The query approach sidesteps both.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public CustomUserDetailsService(UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for email: " + email));
        UserAccount account = user.getUserAccount();
        if (account == null) {
            throw new UsernameNotFoundException("No account for user: " + email);
        }
        return CustomUserDetails.from(user, account, resolveAuthorities(user.getId()), TenantContext.getCurrentTenant().orElse(null));
    }

    /**
     * Resolves the effective granted authorities for a user: the permissions of every
     * directly held role, every role held through an <em>active</em> group, and every
     * transitively inherited parent role (Faz 4a). Pure query-driven — no lazy entity
     * traversal. Callers must run inside a transaction (login / loadUserByUsername
     * provide one).
     *
     * <p>Parent inheritance is resolved iteratively: starting from the seed role ids
     * (direct + active-group) the parent adjacency ({@code t_role_parents}) is walked
     * breadth-first until the closure is stable. The seed set doubles as the visited
     * set, so a malformed inheritance cycle (which {@code RoleService.setParents}
     * already prevents) cannot infinite-loop.
     */
    public Set<GrantedAuthority> resolveAuthorities(UUID userId) {
        return resolvePermissionNames(effectiveRoleIds(userId)).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    /**
     * Seed role ids for a user: direct roles ({@code t_user_roles}) plus roles reached
     * through <em>active</em> groups ({@code t_user_groups} + {@code t_group_roles}).
     * Inactive groups are excluded so a deactivated group drops its permissions. Shared
     * seed for {@link #resolveAuthorities(UUID)} and the user effective-permissions view.
     */
    private Set<UUID> effectiveRoleIds(UUID userId) {
        Set<UUID> roleIds = new HashSet<>();
        roleIds.addAll(userRepository.findDirectRoleIds(userId));
        roleIds.addAll(userRepository.findActiveGroupRoleIds(userId));
        return roleIds;
    }

    /**
     * Effective permission names granted by the given user, expanding the same chain as
     * {@link #resolveAuthorities(UUID)} (direct + active-group roles + transitive parent
     * inheritance). Returns wire strings ({@code {module}:{resource}:{action}}) sorted
     * for stable display. Backs {@code GET /users/{id}/effective-permissions}.
     */
    public List<String> resolveEffectivePermissionNamesForUser(UUID userId) {
        return resolvePermissionNames(effectiveRoleIds(userId));
    }

    /**
     * Resolves the sorted permission-name set granted by the given seed roles, expanding
     * the transitive parent-role closure ({@code t_role_parents}, Faz 4a). Pure
     * query-driven — no lazy entity traversal. Shared by user authority resolution and
     * the group effective-permissions view. Callers must run inside a transaction.
     *
     * <p>Parent inheritance is resolved iteratively: starting from the seed role ids the
     * parent adjacency is walked breadth-first until the closure is stable. The seed set
     * doubles as the visited set, so a malformed inheritance cycle (which
     * {@code RoleService.setParents} already prevents) cannot infinite-loop.
     *
     * <p><strong>All-permissions short-circuit:</strong> after the closure is built, if
     * any role in it carries the {@code all_permissions} flag, the method returns every
     * permission name in the tenant instead of the role-attached set. This makes the
     * built-in {@code Admin} role (seeded with the flag) and any user-defined "ALL" role
     * implicitly hold all permissions — including ones created at runtime — without an
     * explicit {@code t_role_permissions} row per permission. Checking after the closure
     * means a role that (transitively) inherits from an all-permissions role is itself
     * treated as all-permissions.
     */
    public List<String> resolvePermissionNames(Collection<UUID> seedRoleIds) {
        Set<UUID> roleIds = new HashSet<>(seedRoleIds);
        Deque<UUID> frontier = new ArrayDeque<>(roleIds);
        while (!frontier.isEmpty()) {
            List<UUID> batch = new ArrayList<>();
            while (!frontier.isEmpty()) {
                batch.add(frontier.poll());
            }
            for (UUID parentId : userRepository.findParentRoleIds(batch)) {
                if (roleIds.add(parentId)) {
                    frontier.add(parentId);
                }
            }
        }
        if (roleRepository.existsByIdInAndAllPermissionsTrue(roleIds)) {
            return permissionRepository.findAllNames();
        }
        List<String> names = new ArrayList<>(userRepository.findPermissionNamesByRoleIds(roleIds));
        java.util.Collections.sort(names);
        return names;
    }
}
