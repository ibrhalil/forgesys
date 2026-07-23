package com.ibrhalil.systemforge.security;

import com.ibrhalil.systemforge.common.tenant.TenantContext;
import com.ibrhalil.systemforge.entity.Group;
import com.ibrhalil.systemforge.entity.Permission;
import com.ibrhalil.systemforge.entity.Role;
import com.ibrhalil.systemforge.entity.User;
import com.ibrhalil.systemforge.entity.UserAccount;
import com.ibrhalil.systemforge.persistence.repository.UserRepository;
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
        Role r = new Role();
        r.setId(UUID.randomUUID());
        r.setName(name);
        r.setPermissions(permissions);
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
