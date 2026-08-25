package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.PermissionRequest;
import com.ibrhalil.forgesys.dto.PermissionResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Permission_;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.security.SessionRevocationService;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    /** Filterable/sortable attributes of the permission list; {@code q} matches {@code name} + {@code description}. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Permission_.NAME, FilterFieldType.STRING, true)
            .field(Permission_.DESCRIPTION, FilterFieldType.STRING, true)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final PermissionRepository permissionRepository;
    private final PermissionListQueryExecutor permissionListQueryExecutor;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;

    @Transactional(readOnly = true)
    public Page<PermissionResponse> search(String q, List<String> qFields, Pageable pageable) {
        Specification<Permission> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, qFields, List.of());
        return permissionListQueryExecutor.search(spec, pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /permissions/search}. */
    @Transactional(readOnly = true)
    public Page<PermissionResponse> search(SearchRequest request, Pageable pageable) {
        Specification<Permission> spec = FilterSpecifications.from(FILTER_FIELDS, request.q(), request.qFields(),
                request.filters());
        return permissionListQueryExecutor.search(spec, pageable);
    }

    @Transactional(readOnly = true)
    public PermissionResponse findById(UUID id) {
        return toResponse(getPermissionOrThrow(id));
    }

    @Transactional
    @AuditLog(action = "permission_created", entityType = "Permission", entityId = "#result.id", entityName = "#result.name")
    public PermissionResponse create(PermissionRequest request) {
        if (permissionRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.PERMISSION_NAME_TAKEN, "Permission name already exists: " + request.name());
        }
        Permission permission = new Permission();
        permission.setName(request.name());
        permission.setDescription(request.description());
        Permission saved = permissionRepository.save(permission);
        // A new permission joins the all-permissions set, so holders of an all-permissions
        // role (Admin + any "ALL" role) should see it on their next request rather than at
        // their access-token TTL — their outstanding tokens still embed the prior snapshot.
        sessionRevocationService.revokeAllPermissionsRoleHolders();
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "permission_updated", entityType = "Permission", entityId = "#result.id", entityName = "#result.name")
    public PermissionResponse update(java.util.UUID id, PermissionRequest request) {
        Permission permission = getPermissionOrThrow(id);
        boolean renamed = !permission.getName().equals(request.name());
        if (renamed && permissionRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.PERMISSION_NAME_TAKEN, "Permission name already exists: " + request.name());
        }
        permission.setName(request.name());
        permission.setDescription(request.description());
        Permission saved = permissionRepository.save(permission);
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
    @AuditLog(action = "permission_deleted", entityType = "Permission", entityId = "#id", entityName = "")
    public void delete(java.util.UUID id) {
        if (!permissionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Permission not found: " + id);
        }
        if (permissionRepository.isInUse(id)) {
            throw new BusinessException(ErrorCode.PERMISSION_IN_USE,
                    "Permission is still assigned to one or more roles: " + id);
        }
        permissionRepository.deleteById(id);
    }

    private Permission getPermissionOrThrow(java.util.UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));
    }

    private PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getName(), permission.getDescription());
    }
}
