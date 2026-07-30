package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AssignPermissionsRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.RoleRequest;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private SessionRevocationService sessionRevocationService;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository, permissionRepository, auditService, sessionRevocationService);
    }

    @Test
    void createRecordsAudit() {
        when(roleRepository.existsByName("Admin")).thenReturn(false);
        UUID id = UUID.randomUUID();
        when(roleRepository.save(any(Role.class))).thenReturn(roleFixture(id, "Admin"));

        roleService.create(new RoleRequest("Admin", "desc"));

        verify(auditService).record("role_created", "Role", id, "Admin");
    }

    @Test
    void updateRecordsAudit() {
        UUID id = UUID.randomUUID();
        Role role = roleFixture(id, "Admin");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(role);

        roleService.update(id, new RoleRequest("Admin", "desc"));

        verify(auditService).record("role_updated", "Role", id, "Admin");
    }

    @Test
    void deleteRecordsAudit() {
        UUID id = UUID.randomUUID();
        when(roleRepository.existsById(id)).thenReturn(true);

        roleService.delete(id);

        verify(roleRepository).deleteById(id);
        verify(auditService).record("role_deleted", "Role", id, null);
    }

    @Test
    void setPermissionsRecordsAudit() {
        UUID id = UUID.randomUUID();
        Role role = roleFixture(id, "Admin");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(role);

        roleService.setPermissions(id, new AssignPermissionsRequest(List.of()));

        verify(auditService).recordDelta(eq("role_permissions_updated"), eq("Role"), eq(id), eq("Admin"), any(), any());
    }

    @Test
    void setPermissionsRevokesHolders() {
        UUID id = UUID.randomUUID();
        Role role = roleFixture(id, "Admin");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(role);

        roleService.setPermissions(id, new AssignPermissionsRequest(List.of()));

        // Faz 1: a permission delta must drop sessions of every bearer immediately.
        verify(sessionRevocationService).revokeRoleHolders(id);
    }

    @Test
    void deleteRevokesHolders() {
        UUID id = UUID.randomUUID();
        when(roleRepository.existsById(id)).thenReturn(true);

        roleService.delete(id);

        // Revoked BEFORE the soft-delete (findUserIdsByRole filters deleted roles).
        verify(sessionRevocationService).revokeRoleHolders(id);
        verify(roleRepository).deleteById(id);
    }

    @Test
    void updateDoesNotRevokeHolders() {
        UUID id = UUID.randomUUID();
        Role role = roleFixture(id, "Admin");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(role);

        // A name/description change does not alter permissions — no revoke.
        roleService.update(id, new RoleRequest("Admin", "desc"));

        verify(sessionRevocationService, org.mockito.Mockito.never()).revokeRoleHolders(any());
    }

    /* ── Faz 4a role inheritance ── */

    @Test
    void setParentsAssignsParentsRevokesHoldersAndAudits() {
        UUID rId = UUID.randomUUID();
        UUID pId = UUID.randomUUID();
        Role child = roleFixture(rId, "Child");
        Role parent = roleFixture(pId, "Parent");
        when(roleRepository.findById(rId)).thenReturn(Optional.of(child));
        when(roleRepository.findAllById(any())).thenReturn(List.of(parent));
        when(roleRepository.save(any(Role.class))).thenReturn(child);

        roleService.setParents(rId, new AssignRolesRequest(List.of(pId)));

        assertThat(child.getParentRoles()).extracting(Role::getName).containsExactly("Parent");
        verify(sessionRevocationService).revokeRoleHolders(rId);
        verify(auditService).recordDelta(eq("role_parents_updated"), eq("Role"), eq(rId), eq("Child"), any(), any());
    }

    @Test
    void setParentsRejectsSelfParent() {
        UUID rId = UUID.randomUUID();
        Role child = roleFixture(rId, "Child");
        when(roleRepository.findById(rId)).thenReturn(Optional.of(child));
        when(roleRepository.findAllById(any())).thenReturn(List.of(child));

        assertThatThrownBy(() -> roleService.setParents(rId, new AssignRolesRequest(List.of(rId))))
                .isInstanceOf(BusinessException.class);
        verify(sessionRevocationService, org.mockito.Mockito.never()).revokeRoleHolders(any());
    }

    @Test
    void setParentsRejectsInheritanceCycle() {
        UUID rId = UUID.randomUUID();
        UUID pId = UUID.randomUUID();
        Role child = roleFixture(rId, "Child");
        Role parent = roleFixture(pId, "Parent");
        // Parent already inherits from Child -> assigning Child->Parent would close a cycle.
        parent.setParentRoles(new java.util.HashSet<>(Set.of(child)));
        when(roleRepository.findById(rId)).thenReturn(Optional.of(child));
        when(roleRepository.findAllById(any())).thenReturn(List.of(parent));

        assertThatThrownBy(() -> roleService.setParents(rId, new AssignRolesRequest(List.of(pId))))
                .isInstanceOf(BusinessException.class);
        verify(sessionRevocationService, org.mockito.Mockito.never()).revokeRoleHolders(any());
    }

    private Role roleFixture(UUID id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        return role;
    }
}
