package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Verifies authority resolution in {@link CustomUserDetailsService}: effective
 * permissions come from direct user roles + active group roles (inactive groups are
 * skipped), and the tenant schema is captured from {@link TenantContext}.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new CustomUserDetailsService(userRepository);
        TenantContext.setCurrentTenant("tenant_acme");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void resolvesPermissionsFromDirectRolesAndActiveGroups() {
        Permission read = permission("tasks:task:read");
        Permission write = permission("iam:user:write");
        Permission secret = permission("billing:invoice:read");

        Role memberRole = role("member", Set.of(read));
        Role adminRole = role("admin", Set.of(write));
        Role billingRole = role("billing", Set.of(secret));

        Group activeGroup = group("admins", true, Set.of(adminRole));
        Group inactiveGroup = group("excluded", false, Set.of(billingRole));

        User user = user("admin@acme.com", Set.of(memberRole), Set.of(activeGroup, inactiveGroup));
        when(userRepository.findByEmail("admin@acme.com")).thenReturn(Optional.of(user));

        CustomUserDetails details = userDetailsService.loadUserByUsername("admin@acme.com");

        assertThat(details.getUserId()).isEqualTo(user.getId());
        assertThat(details.getUsername()).isEqualTo("admin@acme.com");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getTenantSchema()).isEqualTo("tenant_acme");
        Set<String> authorityNames = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertThat(authorityNames).containsExactlyInAnyOrder("tasks:task:read", "iam:user:write");
    }

    @Test
    void resolvesPermissionsTransitivelyInheritedFromParentRoles() {
        // Faz 4a: a role inherits its parent roles' permissions transitively.
        Role grandparent = role("gp", Set.of(permission("a:read")));
        Role parent = role("p", Set.of(permission("b:read")), Set.of(grandparent));
        Role child = role("c", Set.of(permission("c:read")), Set.of(parent));
        User user = user("u@acme.com", Set.of(child), Set.of());
        when(userRepository.findByEmail("u@acme.com")).thenReturn(Optional.of(user));

        CustomUserDetails details = userDetailsService.loadUserByUsername("u@acme.com");

        Set<String> authorityNames = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertThat(authorityNames).containsExactlyInAnyOrder("a:read", "b:read", "c:read");
    }

    @Test
    void inheritanceCycleTerminatesViaVisitedGuard() {
        // Defense-in-depth: a malformed cycle (which setParents prevents) must not
        // infinite-loop — the shared visited set breaks it.
        Role a = role("a", Set.of(permission("a:x")));
        Role b = role("b", Set.of(permission("b:y")), Set.of(a));
        a.setParentRoles(new java.util.HashSet<>(Set.of(b))); // a -> b -> a
        User user = user("u@acme.com", Set.of(a), Set.of());
        when(userRepository.findByEmail("u@acme.com")).thenReturn(Optional.of(user));

        CustomUserDetails details = userDetailsService.loadUserByUsername("u@acme.com");

        Set<String> authorityNames = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertThat(authorityNames).containsExactlyInAnyOrder("a:x", "b:y");
    }

    @Test
    void unknownEmailThrowsUsernameNotFound() {
        when(userRepository.findByEmail("nobody@acme.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody@acme.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private Permission permission(String name) {
        Permission p = new Permission();
        p.setId(UUID.randomUUID());
        p.setName(name);
        return p;
    }

    private Role role(String name, Set<Permission> permissions) {
        return role(name, permissions, Set.of());
    }

    private Role role(String name, Set<Permission> permissions, Set<Role> parents) {
        Role r = new Role();
        r.setId(UUID.randomUUID());
        r.setName(name);
        r.setPermissions(permissions);
        r.setParentRoles(new java.util.HashSet<>(parents));
        return r;
    }

    private Group group(String name, boolean active, Set<Role> roles) {
        Group g = new Group();
        g.setId(UUID.randomUUID());
        g.setName(name);
        g.setActive(active);
        g.setRoles(roles);
        return g;
    }

    private User user(String email, Set<Role> roles, Set<Group> groups) {
        UserAccount account = new UserAccount();
        account.setEnabled(true);
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setPassword("hash");
        u.setRoles(roles);
        u.setGroups(groups);
        u.setUserAccount(account);
        return u;
    }
}
