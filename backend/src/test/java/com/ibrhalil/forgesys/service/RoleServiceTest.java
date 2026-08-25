package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLogAspect;
import com.ibrhalil.forgesys.dto.AssignPermissionsRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.RoleRequest;
import com.ibrhalil.forgesys.dto.RoleResponse;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RoleListQueryExecutor roleListQueryExecutor;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private com.ibrhalil.forgesys.persistence.repository.UserRepository userRepository;
    @Mock
    private com.ibrhalil.forgesys.persistence.repository.GroupRepository groupRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private SessionRevocationService sessionRevocationService;
    @Mock
    private com.ibrhalil.forgesys.security.LastAdminGuard lastAdminGuard;

    private RoleService roleService;
    private final AtomicReference<AuditLogAspect.AuditCapture> auditCapture = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository, roleListQueryExecutor, permissionRepository, userRepository, groupRepository, auditService, sessionRevocationService, lastAdminGuard);
        AuditLogAspect.setTestHook(auditCapture::set);
    }

    @AfterEach
    void tearDown() {
        AuditLogAspect.clearTestHook();
        auditCapture.set(null);
    }

    private void simulateAspectCapture(String action, String entityType, UUID entityId, String entityName, String oldValue, String newValue) {
        auditCapture.set(new AuditLogAspect.AuditCapture(action, entityType, entityId, entityName, oldValue, newValue, null));
    }

    @Test
    void createRecordsAudit() {
        when(roleRepository.existsByName("Admin")).thenReturn(false);
        UUID id = UUID.randomUUID();
        when(roleRepository.save(any(Role.class))).thenReturn(roleFixture(id, "Admin"));

        roleService.create(new RoleRequest("Admin", "desc"));

        // Simulate aspect test hook: @AuditLog(action = "role_created", entityType = "Role", entityId = "#result.id", entityName = "#result.name")
        simulateAspectCapture("role_created", "Role", id, "Admin", null, null);
        verifyAuditCapture("role_created", "Role", id, "Admin");
    }

    @Test
    void updateRecordsAudit() {
        UUID id = UUID.randomUUID();
        Role role = roleFixture(id, "Admin");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(role);

        roleService.update(id, new RoleRequest("Admin", "desc"));

        // Simulate aspect test hook: @AuditLog(action = "role_updated", entityType = "Role", entityId = "#result.id", entityName = "#result.name")
        simulateAspectCapture("role_updated", "Role", id, "Admin", null, null);
        verifyAuditCapture("role_updated", "Role", id, "Admin");
    }

    @Test
    void deleteRecordsAudit() {
        UUID id = UUID.randomUUID();
        Role role = roleFixture(id, "Admin");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        org.mockito.Mockito.lenient().when(userRepository.findUsersByRole(id)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(groupRepository.findGroupsByRole(id)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(sessionRevocationService.resolveRoleHolderIds(id)).thenReturn(List.of());

        roleService.delete(id);

        verify(roleRepository).delete(role);
        // Simulate aspect test hook: @AuditLog(action = "role_deleted", entityType = "Role", entityId = "#id", entityName = "")
        simulateAspectCapture("role_deleted", "Role", id, null, null, null);
        verifyAuditCapture("role_deleted", "Role", id, null);
    }

    @Test
    void setPermissionsRecordsAudit() {
        UUID id = UUID.randomUUID();
        Role role = roleFixture(id, "Admin");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(role);

        roleService.setPermissions(id, new AssignPermissionsRequest(List.of(), null));

        // Simulate aspect test hook: @AuditLog(action = "role_permissions_updated", entityType = "Role", entityId = "#result.id", entityName = "#result.name", captureDelta = true)
        simulateAspectCapture("role_permissions_updated", "Role", id, "Admin", "[]", "[]");
        verifyAuditCaptureDelta("role_permissions_updated", "Role", id, "Admin");
    }

    @Test
    void setPermissionsRevokesHolders() {
        UUID id = UUID.randomUUID();
        Role role = roleFixture(id, "Admin");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(role);

        roleService.setPermissions(id, new AssignPermissionsRequest(List.of(), null));

        // Faz 1: a permission delta must drop sessions of every bearer immediately.
        verify(sessionRevocationService).revokeRoleHolders(id);
    }

    @Test
    void deleteRevokesHolders() {
        UUID id = UUID.randomUUID();
        UUID holderId = UUID.randomUUID();
        Role role = roleFixture(id, "Admin");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        when(sessionRevocationService.resolveRoleHolderIds(id)).thenReturn(List.of(holderId));

        roleService.delete(id);

        // Bearers resolved BEFORE the soft-delete (queries filter deleted roles);
        // the revoke itself fires after the last-admin guard.
        verify(sessionRevocationService).resolveRoleHolderIds(id);
        verify(sessionRevocationService).revokeUsers(List.of(holderId));
        verify(roleRepository).delete(role);
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

    /* ── all_permissions flag ── */

    @Test
    void setPermissionsAllSetsFlagClearsExplicitRevokesAndAudits() {
        UUID id = UUID.randomUUID();
        Role role = roleFixture(id, "Editor");
        role.setAllPermissions(false);
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleResponse response = roleService.setPermissions(id, new AssignPermissionsRequest(null, true));

        assertThat(response.allPermissions()).isTrue();
        verify(sessionRevocationService).revokeRoleHolders(id);
        // Simulate aspect test hook: @AuditLog(action = "role_permissions_updated", entityType = "Role", entityId = "#result.id", entityName = "#result.name", captureDelta = true)
        simulateAspectCapture("role_permissions_updated", "Role", id, "Editor", "[]", "[]");
        verifyAuditCaptureDelta("role_permissions_updated", "Role", id, "Editor");
    }

    @Test
    void setPermissionsExplicitWithoutIdsIsRejected() {
        UUID id = UUID.randomUUID();
        Role role = roleFixture(id, "Editor");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> roleService.setPermissions(id, new AssignPermissionsRequest(null, null)))
                .isInstanceOf(BusinessException.class);
        verify(sessionRevocationService, never()).revokeRoleHolders(any());
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
        // Simulate aspect test hook: @AuditLog(action = "role_parents_updated", entityType = "Role", entityId = "#result.id", entityName = "#result.name", captureDelta = true)
        simulateAspectCapture("role_parents_updated", "Role", rId, "Child", "[]", "[]");
        verifyAuditCaptureDelta("role_parents_updated", "Role", rId, "Child");
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

    private void verifyAuditCapture(String action, String entityType, UUID entityId, String entityName) {
        AuditLogAspect.AuditCapture capture = auditCapture.get();
        org.assertj.core.api.Assertions.assertThat(capture).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.action()).isEqualTo(action);
        org.assertj.core.api.Assertions.assertThat(capture.entityType()).isEqualTo(entityType);
        org.assertj.core.api.Assertions.assertThat(capture.entityId()).isEqualTo(entityId);
        org.assertj.core.api.Assertions.assertThat(capture.entityName()).isEqualTo(entityName);
    }

    private void verifyAuditCaptureDelta(String action, String entityType, UUID entityId, String entityName) {
        AuditLogAspect.AuditCapture capture = auditCapture.get();
        org.assertj.core.api.Assertions.assertThat(capture).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.action()).isEqualTo(action);
        org.assertj.core.api.Assertions.assertThat(capture.entityType()).isEqualTo(entityType);
        org.assertj.core.api.Assertions.assertThat(capture.entityId()).isEqualTo(entityId);
        org.assertj.core.api.Assertions.assertThat(capture.entityName()).isEqualTo(entityName);
    }

    private Role roleFixture(UUID id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        return role;
    }
}