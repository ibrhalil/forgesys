package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AssignPermissionsRequest;
import com.ibrhalil.forgesys.dto.PermissionResponse;
import com.ibrhalil.forgesys.dto.RoleRequest;
import com.ibrhalil.forgesys.dto.RoleResponse;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;

    @Transactional(readOnly = true)
    public Page<RoleResponse> findAll(Pageable pageable) {
        return roleRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(UUID id) {
        return toResponse(getRoleOrThrow(id));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.ROLE_NAME_TAKEN, "Role name already exists: " + request.name());
        }
        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description());
        Role saved = roleRepository.save(role);
        auditService.record("role_created", "Role", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public RoleResponse update(UUID id, RoleRequest request) {
        Role role = getRoleOrThrow(id);
        if (!role.getName().equals(request.name()) && roleRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.ROLE_NAME_TAKEN, "Role name already exists: " + request.name());
        }
        role.setName(request.name());
        role.setDescription(request.description());
        Role saved = roleRepository.save(role);
        auditService.record("role_updated", "Role", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Role not found: " + id);
        }
        // Faz 1: revoke BEFORE the soft-delete. findUserIdsByRole joins through the role
        // entity, which @SQLRestriction filters out once is_deleted=true — so resolving
        // bearers after deleteById would return nobody and the revoke would silently miss
        // every holder. Every bearer's outstanding tokens still carry the removed role's
        // permissions until their next issue, so kill their sessions now.
        sessionRevocationService.revokeRoleHolders(id);
        roleRepository.deleteById(id);
        auditService.record("role_deleted", "Role", id, null);
    }

    @Transactional
    public RoleResponse setPermissions(UUID roleId, AssignPermissionsRequest request) {
        Role role = getRoleOrThrow(roleId);
        List<UUID> requestedIds = request.permissionIds();
        List<Permission> permissions = requestedIds.isEmpty()
                ? List.of()
                : permissionRepository.findAllById(requestedIds);
        if (permissions.size() != requestedIds.size()) {
            throw new ResourceNotFoundException("One or more permissions not found");
        }
        java.util.Set<String> beforeNames = role.getPermissions().stream()
                .map(Permission::getName).collect(java.util.stream.Collectors.toSet());
        // Mutate the persistent collection (don't replace the reference).
        role.getPermissions().clear();
        role.getPermissions().addAll(permissions);
        Role saved = roleRepository.save(role);
        // Faz 1: a permission delta on this role changes what every bearer can do, but
        // their outstanding tokens still embed the old permission set — revoke so the
        // delta is enforced on the next request, not at access-token TTL.
        sessionRevocationService.revokeRoleHolders(saved.getId());
        // Faz 2b: record the before/after permission set.
        auditService.recordDelta("role_permissions_updated", "Role", saved.getId(), saved.getName(),
                AuditService.namesJson(beforeNames),
                AuditService.namesJson(permissions.stream().map(Permission::getName).collect(java.util.stream.Collectors.toSet())));
        return toResponse(saved);
    }

    private Role getRoleOrThrow(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
    }

    private RoleResponse toResponse(Role role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .map(permission -> new PermissionResponse(
                        permission.getId(), permission.getName(), permission.getDescription()))
                .sorted(Comparator.comparing(PermissionResponse::name))
                .toList();
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), permissions);
    }
}
