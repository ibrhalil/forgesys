package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AssignPermissionsRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.PermissionResponse;
import com.ibrhalil.forgesys.dto.RoleRequest;
import com.ibrhalil.forgesys.dto.RoleResponse;
import com.ibrhalil.forgesys.dto.RoleSummary;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.BaseEntity_;
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

    /**
     * Filterable/sortable attributes of the role list (K-49); {@code q} matches
     * {@code name}/{@code description}. {@code permissionCount} counts explicit grants
     * only (0 for all-permissions roles).
     */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Role_.NAME, FilterFieldType.STRING, true)
            .field(Role_.DESCRIPTION, FilterFieldType.STRING, true)
            .field(Role_.ALL_PERMISSIONS, FilterFieldType.BOOLEAN, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .subqueryField("permissionCount", FilterFieldType.NUMERIC, false,
                    UserDirectoryQueryExecutor.countMembers(Role_.PERMISSIONS))
            .membershipField("permissionIds", Role_.PERMISSIONS, BaseEntity_.ID)
            .membershipField("parentIds", Role_.PARENT_ROLES, BaseEntity_.ID)
            .build();

    private final RoleRepository roleRepository;
    private final RoleListQueryExecutor roleListQueryExecutor;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;
    private final LastAdminGuard lastAdminGuard;

    /** Audit-delta sentinel marking that a role carries the {@code all_permissions} flag. */
    private static final String ALL_PERMISSIONS_SENTINEL = "ALL_PERMISSIONS";

    @Transactional(readOnly = true)
    public Page<RoleResponse> search(String q, List<String> qFields, Pageable pageable) {
        Specification<Role> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, qFields, List.of());
        return roleListQueryExecutor.search(spec, pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /roles/search}. */
    @Transactional(readOnly = true)
    public Page<RoleResponse> search(SearchRequest request, Pageable pageable) {
        Specification<Role> spec = FilterSpecifications.from(FILTER_FIELDS, request.q(), request.qFields(),
                request.filters());
        return roleListQueryExecutor.search(spec, pageable);
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
        // Detach from every referencing collection BEFORE the soft-delete — leftover
        // join rows fail the flush (TransientPropertyValueException) and orphan rows.
        for (User holder : userRepository.findUsersByRole(id)) {
            holder.getRoles().remove(role);
        }
        for (Group carrier : groupRepository.findGroupsByRole(id)) {
            carrier.getRoles().remove(role);
        }
        // Resolve revoke targets while the role is still visible to the queries.
        List<UUID> holderIds = sessionRevocationService.resolveRoleHolderIds(id);
        roleRepository.delete(role);
        // Guard AFTER the soft-delete flush and BEFORE the revoke — a rejection rolls
        // back the whole tx without leaving Redis-side carnage behind.
        lastAdminGuard.assertActiveAdminExists();
        sessionRevocationService.revokeUsers(holderIds);
    }

    @Transactional
    @AuditLog(action = "role_permissions_updated", entityType = "Role", entityId = "#result.id", entityName = "#result.name",
            captureDelta = true)
    public RoleResponse setPermissions(UUID roleId, AssignPermissionsRequest request) {
        Role role = getRoleOrThrow(roleId);
        boolean all = Boolean.TRUE.equals(request.all());
        // Before-state for the audit delta (ALL_PERMISSIONS sentinel = the all-mode).
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
            // Mutate the persistent collection — do not replace the reference.
            role.getPermissions().clear();
            role.getPermissions().addAll(permissions);
        }
        // Guard BEFORE save: clearing the all-permissions flag (or emptying the role)
        // may drop below the one-active-admin floor (auto-flushed first).
        lastAdminGuard.assertActiveAdminExists();
        Role saved = roleRepository.save(role);
        // Revoke holders: their outstanding tokens still embed the old permission set.
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
     * Faz 4a: replaces the role's parents; acyclicity enforced (no self-parent, no
     * transitive back-edge). Holders revoked; before/after parent set audited.
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
        // Guard: breaking an inheritance edge may strip admin-capability (auto-flushed first).
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
