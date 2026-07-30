package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.PermissionRequest;
import com.ibrhalil.forgesys.dto.PermissionResponse;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll() {
        return permissionRepository.findAllByOrderByNameAsc().stream()
                .map(permission -> new PermissionResponse(
                        permission.getId(), permission.getName(), permission.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PermissionResponse findById(java.util.UUID id) {
        return toResponse(getPermissionOrThrow(id));
    }

    @Transactional
    public PermissionResponse create(PermissionRequest request) {
        if (permissionRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.PERMISSION_NAME_TAKEN, "Permission name already exists: " + request.name());
        }
        Permission permission = new Permission();
        permission.setName(request.name());
        permission.setDescription(request.description());
        Permission saved = permissionRepository.save(permission);
        auditService.record("permission_created", "Permission", saved.getId(), saved.getName());
        // A new permission joins the all-permissions set, so holders of an all-permissions
        // role (Admin + any "ALL" role) should see it on their next request rather than at
        // their access-token TTL — their outstanding tokens still embed the prior snapshot.
        sessionRevocationService.revokeAllPermissionsRoleHolders();
        return toResponse(saved);
    }

    @Transactional
    public PermissionResponse update(java.util.UUID id, PermissionRequest request) {
        Permission permission = getPermissionOrThrow(id);
        boolean renamed = !permission.getName().equals(request.name());
        if (renamed && permissionRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.PERMISSION_NAME_TAKEN, "Permission name already exists: " + request.name());
        }
        permission.setName(request.name());
        permission.setDescription(request.description());
        Permission saved = permissionRepository.save(permission);
        auditService.record("permission_updated", "Permission", saved.getId(), saved.getName());
        // A rename changes the authority string every all-permissions user carries, so
        // refresh their tokens to embed the new name (same rationale as create).
        if (renamed) {
            sessionRevocationService.revokeAllPermissionsRoleHolders();
        }
        return toResponse(saved);
    }

    /**
     * Blocks while the permission is still assigned to any role ({@code isInUse}). A
     * permission in use is part of the live RBAC graph: deleting it would silently
     * shrink every bearer's authority set. Callers must unassign it from all roles first
     * (which already triggers session revoke via {@code RoleService.setPermissions}).
     */
    @Transactional
    public void delete(java.util.UUID id) {
        if (!permissionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Permission not found: " + id);
        }
        if (permissionRepository.isInUse(id)) {
            throw new BusinessException(ErrorCode.PERMISSION_IN_USE,
                    "Permission is still assigned to one or more roles: " + id);
        }
        permissionRepository.deleteById(id);
        auditService.record("permission_deleted", "Permission", id, null);
    }

    private Permission getPermissionOrThrow(java.util.UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));
    }

    private PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getName(), permission.getDescription());
    }
}
