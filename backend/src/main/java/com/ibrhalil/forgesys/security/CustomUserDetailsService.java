package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Loads a user (by email, within the current tenant schema resolved by
 * {@code TenantFilter}) and resolves its effective authorities:
 * <strong>direct user roles</strong> + <strong>active group roles</strong>, each
 * expanded to their permissions ({@code {module}:{resource}:{action}}).
 *
 * <p>{@link #loadUserByUsername(String)} is the {@link UserDetailsService} contract.
 * {@link #resolveAuthorities(User)} is shared authority resolution invoked by
 * {@code AuthService} at login. The JWT filter reconstructs the principal from claims
 * on subsequent requests (no DB load), so permission changes apply on the next token.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        return CustomUserDetails.from(user, account, resolveAuthorities(user), TenantContext.getCurrentTenant().orElse(null));
    }

    public static Set<GrantedAuthority> resolveAuthorities(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        for (Role role : user.getRoles()) {
            addPermissions(authorities, role);
        }
        for (Group group : user.getGroups()) {
            if (group.isActive()) {
                for (Role role : group.getRoles()) {
                    addPermissions(authorities, role);
                }
            }
        }
        return authorities;
    }

    private static void addPermissions(Set<GrantedAuthority> authorities, Role role) {
        for (Permission permission : role.getPermissions()) {
            authorities.add(new SimpleGrantedAuthority(permission.getName()));
        }
    }
}
