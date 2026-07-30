package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the RBAC seed privilege-escalation fix (2026-08-16): startup seeding
 * must NEVER touch user role assignments — a role-less user is a deliberately
 * unprivileged user, not a candidate admin. Admin is granted only explicitly via
 * {@link RbacSeeder#assignAdminTo(User)} from tenant provisioning.
 */
@ExtendWith(MockitoExtension.class)
class RbacSeederTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private ObjectProvider<RbacSeeder> self;

    private RbacSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new RbacSeeder(roleRepository, permissionRepository, userRepository, companyRepository, self);
    }

    /**
     * Regression: the pre-fix seeder granted the all_permissions Admin role to every
     * role-less user on each restart — a deliberately unprivileged user silently became
     * full admin after a restart (audit: user1@abc.com modified the tenant admin).
     */
    @Test
    void seedForCurrentTenant_neverAssignsRolesToUsers() {
        stubCatalogPresent();

        seeder.seedForCurrentTenant();

        // The seed only syncs the permission catalog + Admin role; user assignments
        // are untouchable at startup.
        verifyNoInteractions(userRepository);
    }

    @Test
    void seedForCurrentTenant_keepsAdminRoleAllPermissionsWithNoExplicitGrants() {
        Role adminRole = stubCatalogPresent();

        seeder.seedForCurrentTenant();

        assertThat(adminRole.isAllPermissions()).isTrue();
        assertThat(adminRole.getPermissions()).isEmpty();
        verify(roleRepository).save(adminRole);
    }

    @Test
    void assignAdminTo_grantsAdminRoleToUser() {
        Role adminRole = stubAdminRole();

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@new-tenant.com");

        seeder.assignAdminTo(user);

        assertThat(user.getRoles()).containsExactly(adminRole);
        verify(userRepository).save(user);
    }

    @Test
    void assignAdminTo_isIdempotentWhenUserAlreadyAdmin() {
        Role adminRole = stubAdminRole();

        User user = new User();
        user.setId(UUID.randomUUID());
        user.getRoles().add(adminRole);

        seeder.assignAdminTo(user);

        assertThat(user.getRoles()).hasSize(1);
        verify(userRepository, never()).save(any(User.class));
    }

    // --- helpers ---------------------------------------------------------

    /** Catalog fully present in the tenant — seed must be a no-op beyond the flag re-sync. */
    private Role stubCatalogPresent() {
        when(permissionRepository.findByName(anyString())).thenAnswer(inv -> {
            Permission existing = new Permission();
            existing.setName(inv.getArgument(0));
            return Optional.of(existing);
        });
        return stubAdminRole();
    }

    private Role stubAdminRole() {
        Role adminRole = new Role();
        adminRole.setId(UUID.randomUUID());
        adminRole.setName(PermissionCatalog.ADMIN_ROLE_NAME);
        when(roleRepository.findByName(PermissionCatalog.ADMIN_ROLE_NAME)).thenReturn(Optional.of(adminRole));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        return adminRole;
    }
}
