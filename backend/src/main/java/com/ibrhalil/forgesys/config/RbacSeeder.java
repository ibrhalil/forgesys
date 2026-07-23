package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Idempotent RBAC seed for tenant schemas. Ensures every tenant owns the built-in
 * permission catalog (see {@link PermissionCatalog}) and an {@code Admin} role that
 * carries all of those permissions, then assigns the Admin role to any role-less user
 * (covers the provisioned admin, including the system tenant admin, and any user created
 * before RBAC seeding existed).
 *
 * <p>Runs at startup (iterating {@code t_companies} and switching {@link TenantContext}
 * per tenant, mirroring {@code TenantMigrationRunner}) and is also invoked directly by
 * {@code TenantProvisioningService.createAdminUser} right after a tenant is provisioned,
 * so a brand-new tenant is seed-complete before the request returns. Disabled in the
 * {@code test} profile (seed data is built manually in tests).
 *
 * <p>{@link #seedForCurrentTenant()} is {@code @Transactional} — called through the
 * Spring proxy from {@link #run(ApplicationArguments)} (via {@code ObjectProvider}) and
 * from {@code TenantProvisioningService} to ensure the session stays open for lazy
 * collection initialization ({@code Role.permissions}, {@code User.roles}).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RbacSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ObjectProvider<RbacSeeder> self;

    @Override
    public void run(ApplicationArguments args) {
        List<Company> companies = companyRepository.findAll();
        for (Company company : companies) {
            String schemaName = company.getSchemaName();
            if (schemaName == null || schemaName.isBlank()) {
                log.warn("Skipping RBAC seed for tenant with blank schema name: id={}", company.getId());
                continue;
            }
            try {
                TenantContext.setCurrentTenant(schemaName);
                self.getObject().seedForCurrentTenant();
            } catch (Exception e) {
                log.error("Failed to seed RBAC for tenant schema: {}", schemaName, e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * Ensures the permission catalog, the Admin role (with every permission) and Admin
     * assignment for role-less users, in the <em>current</em> tenant context. The caller
     * is responsible for setting/clearing {@link TenantContext}.
     */
    @Transactional
    public void seedForCurrentTenant() {
        Map<String, Permission> permissions = ensurePermissions();
        Role adminRole = ensureAdminRole(permissions);
        assignAdminToRoleLessUsers(adminRole);
    }

    private Map<String, Permission> ensurePermissions() {
        return PermissionCatalog.ALL.stream()
                .map(this::ensurePermission)
                .collect(Collectors.toUnmodifiableMap(Permission::getName, Function.identity()));
    }

    private Permission ensurePermission(PermissionCatalog.PermissionDefinition definition) {
        return permissionRepository.findByName(definition.name())
                .orElseGet(() -> {
                    Permission permission = new Permission();
                    permission.setName(definition.name());
                    permission.setDescription(definition.description());
                    log.info("Seeding permission: {}", definition.name());
                    return permissionRepository.save(permission);
                });
    }

    private Role ensureAdminRole(Map<String, Permission> permissions) {
        Role adminRole = roleRepository.findByName(PermissionCatalog.ADMIN_ROLE_NAME)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(PermissionCatalog.ADMIN_ROLE_NAME);
                    role.setDescription("Full administrative access (all built-in permissions)");
                    return role;
                });
        // Mutate the existing collection (don't replace the persistent reference).
        adminRole.getPermissions().clear();
        adminRole.getPermissions().addAll(permissions.values());
        return roleRepository.save(adminRole);
    }

    private void assignAdminToRoleLessUsers(Role adminRole) {
        List<User> roleLessUsers = userRepository.findByRolesEmpty();
        for (User user : roleLessUsers) {
            user.getRoles().add(adminRole);
            userRepository.save(user);
            log.info("Assigned Admin role to role-less user: {}", user.getEmail());
        }
    }
}
