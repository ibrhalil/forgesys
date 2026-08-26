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
 * Loads a user by email within the current tenant and resolves effective authorities:
 * direct roles + active group roles + transitive parent inheritance (Faz 4a), expanded
 * to permission names. Purely query-driven — no lazy entity traversal. The JWT filter
 * rebuilds the principal from claims, so permission changes apply on the next token issue.
 * rationale: docs/CODE_NOTES.md (backend/security → CustomUserDetailsService)
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
     * Effective granted authorities (direct + active-group + parent-inherited roles).
     * Callers must run inside a transaction.
     */
    public Set<GrantedAuthority> resolveAuthorities(UUID userId) {
        return resolvePermissionNames(effectiveRoleIds(userId)).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    /** Seed role ids: direct roles + roles through active groups (inactive groups drop their permissions). */
    private Set<UUID> effectiveRoleIds(UUID userId) {
        Set<UUID> roleIds = new HashSet<>();
        roleIds.addAll(userRepository.findDirectRoleIds(userId));
        roleIds.addAll(userRepository.findActiveGroupRoleIds(userId));
        return roleIds;
    }

    /** Effective permission names for a user; backs {@code GET /users/{id}/effective-permissions}. */
    public List<String> resolveEffectivePermissionNamesForUser(UUID userId) {
        return resolvePermissionNames(effectiveRoleIds(userId));
    }

    /**
     * Sorted permission names of the seed roles, expanding the transitive parent closure
     * ({@code t_role_parents}, BFS; the seed set doubles as visited so cycles cannot loop).
     * If any closure member carries {@code all_permissions}, every tenant permission is
     * returned instead (dynamic resolution — runtime-created permissions included).
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
