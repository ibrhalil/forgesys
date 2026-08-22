package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
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

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Idempotent RBAC seed for tenant schemas. Ensures every tenant owns the built-in
 * <em>core</em> permission catalog (see {@link PermissionCatalog#CORE} — module-owned
 * permissions are seeded on module activation, K-16) and an {@code Admin} role that
 * carries the {@code all_permissions} flag (so it implicitly holds every permission,
 * resolved dynamically — no per-permission grant rows).
 *
 * <p>Runs at startup (iterating {@code t_companies} and switching {@link TenantContext}
 * per tenant, mirroring {@code TenantMigrationRunner}) and is also invoked directly by
 * {@code TenantProvisioningService.createAdminUser} right after a tenant is provisioned,
 * so a brand-new tenant is seed-complete before the request returns. Disabled in the
 * {@code test} profile (seed data is built manually in tests).
 *
 * <p><strong>Never grants roles at startup.</strong> The Admin role is assigned ONLY
 * explicitly, by {@link #assignAdminTo(User)} from {@code TenantProvisioningService}
 * when a tenant's first admin is created. Auto-assigning Admin to role-less users at
 * startup silently elevated deliberately unprivileged users to full admin on every
 * restart — closed (2026-08-16).
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
        for (CompanyRepository.TenantSchemaView tenant : companyRepository.findAllTenantSchemas()) {
            String schemaName = tenant.getSchemaName();
            if (schemaName == null || schemaName.isBlank()) {
                log.warn("Skipping RBAC seed for tenant with blank schema name: id={}", tenant.getId());
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
     * Ensures the permission catalog and the Admin role (carrying the {@code all_permissions}
     * flag) in the <em>current</em> tenant context. Does NOT touch user assignments —
     * Admin is granted only explicitly via {@link #assignAdminTo(User)}. The caller is
     * responsible for setting/clearing {@link TenantContext}.
     */
    @Transactional
    public void seedForCurrentTenant() {
        ensurePermissions();
        ensureAdminRole();
    }

    private Map<String, Permission> ensurePermissions() {
        return PermissionCatalog.CORE.stream()
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

    private Role ensureAdminRole() {
        Role adminRole = roleRepository.findByName(PermissionCatalog.ADMIN_ROLE_NAME)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(PermissionCatalog.ADMIN_ROLE_NAME);
                    role.setDescription("Full administrative access (implicit all-permissions role)");
                    return role;
                });
        // The Admin role carries every permission implicitly via the all_permissions flag
        // (resolved dynamically by CustomUserDetailsService), so it needs no explicit
        // t_role_permissions rows. Keeping them out means deleting a catalog permission
        // is never blocked as "in use" by the Admin role.
        adminRole.setAllPermissions(true);
        adminRole.getPermissions().clear();
        return roleRepository.save(adminRole);
    }

    /**
     * Explicitly grants the {@code all_permissions} Admin role to the given user (the
     * tenant's first admin, called from {@code TenantProvisioningService.createAdminUser}
     * within the tenant context). Idempotent for users already holding the role.
     */
    @Transactional
    public void assignAdminTo(User user) {
        Role adminRole = ensureAdminRole();
        if (user.getRoles().stream().noneMatch(r -> r.getId().equals(adminRole.getId()))) {
            user.getRoles().add(adminRole);
            userRepository.save(user);
            log.info("Assigned Admin role to user: {}", user.getEmail());
        }
    }
}
