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
 * Idempotent RBAC seed per tenant: the built-in IAM permission catalog (module
 * permissions seed on activation, K-16) + an {@code Admin} role carrying
 * {@code all_permissions} (resolved dynamically — no grant rows). Also invokes directly
 * by provisioning so a new tenant is seed-complete. NEVER grants roles at startup:
 * Admin is assigned ONLY via {@link #assignAdminTo} — startup auto-assign silently
 * elevated role-less users on every restart (closed 2026-08-16).
 * RISK-18 closure (K-50 F3): startup also purges retired {@code platform:*}
 * permission rows from every tenant schema (the platform catalog lives in code —
 * {@link PlatformPermissionCatalog}).
 * rationale: docs/CODE_NOTES.md (backend/config → RbacSeeder)
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

    /** Ensures catalog + Admin role in the CURRENT tenant context; caller sets/clears {@link TenantContext}. */
    @Transactional
    public void seedForCurrentTenant() {
        ensurePermissions();
        ensureAdminRole();
    }

    private Map<String, Permission> ensurePermissions() {
        return PermissionCatalog.IAM_PERMISSIONS.stream()
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
        // all_permissions ⇒ no t_role_permissions rows: deleting a catalog permission
        // is never blocked as "in use" by the Admin role.
        adminRole.setAllPermissions(true);
        adminRole.getPermissions().clear();
        return roleRepository.save(adminRole);
    }

    /** Explicitly grants the Admin role to the given user (tenant's first admin); idempotent. */
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
