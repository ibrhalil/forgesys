package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AssignPermissionsRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.PermissionResponse;
import com.ibrhalil.forgesys.dto.RoleRequest;
import com.ibrhalil.forgesys.dto.RoleResponse;
import com.ibrhalil.forgesys.dto.RoleSummary;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.Role_;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.audit.AuditDeltaContext;
import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import com.ibrhalil.forgesys.security.LastAdminGuard;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    /** Filterable/sortable direct attributes of the role list; {@code q} matches {@code name}. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Role_.NAME, FilterFieldType.STRING, true)
            .field(Role_.DESCRIPTION, FilterFieldType.STRING, false)
            .field(Role_.ALL_PERMISSIONS, FilterFieldType.BOOLEAN, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;
    private final LastAdminGuard lastAdminGuard;

    /** Audit-delta sentinel marking that a role carries the {@code all_permissions} flag. */
    private static final String ALL_PERMISSIONS_SENTINEL = "ALL_PERMISSIONS";

    @Transactional(readOnly = true)
    public Page<RoleResponse> search(String q, Pageable pageable) {
        Specification<Role> spec = FilterSpecifications.from(FILTER_FIELDS, StringUtils.hasText(q) ? q.trim() : null, List.of());
        return roleRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(UUID id) {
        return toResponse(getRoleOrThrow(id));
    }

    @Transactional
    @AuditLog(action = "role_created", entityType = "Role", entityId = "#result.id", entityName = "#result.name")
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.ROLE_NAME_TAKEN, "Role name already exists: " + request.name());
        }
        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description());
        Role saved = roleRepository.save(role);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "role_updated", entityType = "Role", entityId = "#result.id", entityName = "#result.name")
    public RoleResponse update(UUID id, RoleRequest request) {
        Role role = getRoleOrThrow(id);
        if (!role.getName().equals(request.name()) && roleRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.ROLE_NAME_TAKEN, "Role name already exists: " + request.name());
        }
        role.setName(request.name());
        role.setDescription(request.description());
        Role saved = roleRepository.save(role);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "role_deleted", entityType = "Role", entityId = "#id", entityName = "")
    public void delete(UUID id) {
        Role role = getRoleOrThrow(id);
        // Detach the role from every referencing collection BEFORE the soft-delete.
        // t_user_roles/t_group_roles are owned by User.roles/Group.roles; leaving the
        // join rows behind keeps managed collections referencing a deleted role, which
        // fails the flush with TransientPropertyValueException (and leaves orphan rows).
        for (User holder : userRepository.findUsersByRole(id)) {
            holder.getRoles().remove(role);
        }
        for (Group carrier : groupRepository.findGroupsByRole(id)) {
            carrier.getRoles().remove(role);
        }
        // Resolve revoke targets (direct + via active groups) while the role is still
        // visible to the queries; the revoke itself fires after the guard.
        List<UUID> holderIds = sessionRevocationService.resolveRoleHolderIds(id);
        roleRepository.delete(role);
        // Last-admin guard AFTER the soft-delete (auto-flushed): the deleted role is
        // invisible to the admin-closure queries, so deleting the last admin-capable
        // role — or one whose only remaining enabled holder was its last admin — is
        // rejected and the whole tx rolls back. Runs BEFORE the revoke so a rejected
        // delete leaves no Redis-side refresh-token carnage behind.
        lastAdminGuard.assertActiveAdminExists();
        sessionRevocationService.revokeUsers(holderIds);
    }

    @Transactional
    @AuditLog(action = "role_permissions_updated", entityType = "Role", entityId = "#result.id", entityName = "#result.name",
            captureDelta = true)
    public RoleResponse setPermissions(UUID roleId, AssignPermissionsRequest request) {
        Role role = getRoleOrThrow(roleId);
        boolean all = Boolean.TRUE.equals(request.all());
        // Capture the before-state for the audit delta (ALL_PERMISSIONS sentinel flags the
        // all-permissions mode; otherwise the explicit permission-name set).
        java.util.Set<String> beforeNames = role.isAllPermissions()
                ? java.util.Set.of(ALL_PERMISSIONS_SENTINEL)
                : role.getPermissions().stream()
                        .map(Permission::getName).collect(java.util.stream.Collectors.toSet());
        if (all) {
            role.setAllPermissions(true);
            role.getPermissions().clear();
        } else {
            List<UUID> requestedIds = request.permissionIds();
            if (requestedIds == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "permissionIds must be present when 'all' is not true");
            }
            List<Permission> permissions = requestedIds.isEmpty()
                    ? List.of()
                    : permissionRepository.findAllById(requestedIds);
            if (permissions.size() != requestedIds.size()) {
                throw new ResourceNotFoundException("One or more permissions not found");
            }
            role.setAllPermissions(false);
            // Mutate the persistent collection (don't replace the reference).
            role.getPermissions().clear();
            role.getPermissions().addAll(permissions);
        }
        // Last-admin guard BEFORE save: clearing the {@code all_permissions} flag (or
        // emptying the role) may drop every admin below the one-active-admin floor —
        // the closure queries auto-flush the pending flag/collection change first.
        lastAdminGuard.assertActiveAdminExists();
        Role saved = roleRepository.save(role);
        // Faz 1: a permission delta on this role changes what every bearer can do, but
        // their outstanding tokens still embed the old permission set — revoke so the
        // delta is enforced on the next request, not at access-token TTL.
        sessionRevocationService.revokeRoleHolders(saved.getId());
        // Faz 2b: set delta values for AOP aspect
        java.util.Set<String> afterNames = saved.isAllPermissions()
                ? java.util.Set.of(ALL_PERMISSIONS_SENTINEL)
                : saved.getPermissions().stream()
                        .map(Permission::getName).collect(java.util.stream.Collectors.toSet());
        AuditDeltaContext.setOldValue(AuditService.namesJson(beforeNames));
        AuditDeltaContext.setNewValue(AuditService.namesJson(afterNames));
        return toResponse(saved);
    }

    private Role getRoleOrThrow(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
    }

    /**
     * Faz 4a: replace this role's inherited parent roles. Acyclicity is enforced — no
     * self-parent, and no candidate may transitively inherit from this role (else the
     * assignment would create a cycle). A parent delta changes the effective permission
     * set of every bearer, so holders are revoked (Faz 1) and the before/after parent set
     * is audited (Faz 2b).
     */
    @Transactional
    @AuditLog(action = "role_parents_updated", entityType = "Role", entityId = "#result.id", entityName = "#result.name",
            captureDelta = true)
    public RoleResponse setParents(UUID roleId, AssignRolesRequest request) {
        Role role = getRoleOrThrow(roleId);
        List<Role> parents = resolveParents(request.roleIds());
        for (Role parent : parents) {
            if (parent.getId().equals(role.getId())) {
                throw new BusinessException(ErrorCode.ROLE_PARENT_CYCLE, "A role cannot inherit from itself");
            }
            if (reaches(parent, role.getId(), new java.util.HashSet<>())) {
                throw new BusinessException(ErrorCode.ROLE_PARENT_CYCLE,
                        "Parent role '" + parent.getName() + "' already inherits from '" + role.getName() + "'");
            }
        }
        java.util.Set<String> beforeNames = role.getParentRoles().stream()
                .map(Role::getName).collect(java.util.stream.Collectors.toSet());
        role.getParentRoles().clear();
        role.getParentRoles().addAll(parents);
        // Last-admin guard: breaking an inheritance edge (e.g. removing an
        // all-permissions parent) strips admin-capability from this role's holders —
        // the downward-closure query auto-flushes the pending t_role_parents change.
        lastAdminGuard.assertActiveAdminExists();
        Role saved = roleRepository.save(role);
        sessionRevocationService.revokeRoleHolders(saved.getId());
        // Faz 2b: set delta values for AOP aspect
        AuditDeltaContext.setOldValue(AuditService.namesJson(beforeNames));
        AuditDeltaContext.setNewValue(AuditService.namesJson(parents.stream().map(Role::getName).collect(java.util.stream.Collectors.toSet())));
        return toResponse(saved);
    }

    private List<Role> resolveParents(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> distinct = new LinkedHashSet<>(ids);
        List<Role> parents = roleRepository.findAllById(distinct);
        if (parents.size() != distinct.size()) {
            throw new ResourceNotFoundException("One or more parent roles not found");
        }
        return parents;
    }

    /** True if {@code targetId} is reachable from {@code start} by following parent links. */
    private boolean reaches(Role start, UUID targetId, Set<UUID> visited) {
        if (start == null || start.getId() == null || !visited.add(start.getId())) {
            return false;
        }
        for (Role parent : start.getParentRoles()) {
            if (targetId.equals(parent.getId())) {
                return true;
            }
            if (reaches(parent, targetId, visited)) {
                return true;
            }
        }
        return false;
    }

    private RoleResponse toResponse(Role role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .map(permission -> new PermissionResponse(
                        permission.getId(), permission.getName(), permission.getDescription()))
                .sorted(Comparator.comparing(PermissionResponse::name))
                .toList();
        List<RoleSummary> parents = role.getParentRoles().stream()
                .map(p -> new RoleSummary(p.getId(), p.getName()))
                .sorted(Comparator.comparing(RoleSummary::name))
                .toList();
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(),
                role.isAllPermissions(), permissions, parents);
    }
}
