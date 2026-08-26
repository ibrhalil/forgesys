package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLogAspect;
import com.ibrhalil.forgesys.dto.AssignMembersRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.GroupRequest;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import com.ibrhalil.forgesys.security.CustomUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupListQueryExecutor groupListQueryExecutor;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private SessionRevocationService sessionRevocationService;
    @Mock
    private CustomUserDetailsService customUserDetailsService;
    @Mock
    private com.ibrhalil.forgesys.security.LastAdminGuard lastAdminGuard;

    private GroupService groupService;
    private final AtomicReference<AuditLogAspect.AuditCapture> auditCapture = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository, groupListQueryExecutor, roleRepository, userRepository, auditService, sessionRevocationService, customUserDetailsService, lastAdminGuard);
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
        when(groupRepository.existsByName("Engineers")).thenReturn(false);
        UUID id = UUID.randomUUID();
        when(groupRepository.save(any(Group.class))).thenReturn(groupFixture(id, "Engineers"));
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.create(new GroupRequest("Engineers", "desc", true));

        // Simulate aspect test hook: @AuditLog(action = "group_created", entityType = "Group", entityId = "#result.id", entityName = "#result.name")
        simulateAspectCapture("group_created", "Group", id, "Engineers", null, null);
        verifyAuditCapture("group_created", "Group", id, "Engineers");
    }

    @Test
    void updateRecordsAudit() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenReturn(group);
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.update(id, new GroupRequest("Engineers", "desc", true));

        // Simulate aspect test hook: @AuditLog(action = "group_updated", entityType = "Group", entityId = "#result.id", entityName = "#result.name")
        simulateAspectCapture("group_updated", "Group", id, "Engineers", null, null);
        verifyAuditCapture("group_updated", "Group", id, "Engineers");
    }

    @Test
    void deleteRecordsAudit() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(userRepository.findGroupMembers(id)).thenReturn(List.of());

        groupService.delete(id);

        verify(groupRepository).delete(group);
        // Simulate aspect test hook: @AuditLog(action = "group_deleted", entityType = "Group", entityId = "#id", entityName = "")
        simulateAspectCapture("group_deleted", "Group", id, null, null, null);
        verifyAuditCapture("group_deleted", "Group", id, null);
    }

    @Test
    void setRolesRecordsAudit() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenReturn(group);
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.setRoles(id, new AssignRolesRequest(List.of()));

        // Simulate aspect test hook: @AuditLog(action = "group_roles_updated", entityType = "Group", entityId = "#result.id", entityName = "#result.name", captureDelta = true)
        simulateAspectCapture("group_roles_updated", "Group", id, "Engineers", "[]", "[]");
        verifyAuditCaptureDelta("group_roles_updated", "Group", id, "Engineers");
    }

    @Test
    void setMembersRecordsAudit() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(userRepository.findGroupMembers(id)).thenReturn(List.of());
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.setMembers(id, new AssignMembersRequest(List.of()));

        // Simulate aspect test hook: @AuditLog(action = "group_members_updated", entityType = "Group", entityId = "#group.id", entityName = "#group.name")
        simulateAspectCapture("group_members_updated", "Group", id, "Engineers", null, null);
        verifyAuditCapture("group_members_updated", "Group", id, "Engineers");
    }

    @Test
    void setRolesRevokesMembers() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenReturn(group);
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.setRoles(id, new AssignRolesRequest(List.of()));

        verify(sessionRevocationService).revokeGroupMembers(id);
    }

    @Test
    void deleteRevokesMembers() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        UUID memberId = UUID.randomUUID();
        User member = new User();
        member.setId(memberId);
        member.getGroups().add(group);
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(userRepository.findGroupMembers(id)).thenReturn(List.of(member));

        groupService.delete(id);

        // Membership detached (join rows owned by User.groups) and the member's
        // sessions revoked AFTER the last-admin guard.
        assertThat(member.getGroups()).isEmpty();
        verify(sessionRevocationService).revokeUsers(List.of(memberId));
        verify(groupRepository).delete(group);
    }

    @Test
    void setMembersRevokesOnlyRemovedUsers() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        UUID removedId = UUID.randomUUID();
        User removed = new User();
        removed.setId(removedId);
        removed.getGroups().add(group); // currently a member, will be removed (empty target)
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(userRepository.findGroupMembers(id)).thenReturn(List.of(removed));
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.setMembers(id, new AssignMembersRequest(List.of()));

        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(sessionRevocationService).revokeUsers(captor.capture());
        assertTrue(captor.getValue().contains(removedId),
                "only removed members (those losing permissions) should be revoked");
    }

    @Test
    void updateDeactivateRevokesMembers() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers"); // active = true
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenReturn(group);
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.update(id, new GroupRequest("Engineers", "desc", false));

        verify(sessionRevocationService).revokeGroupMembers(id);
    }

    @Test
    void updateActivateDoesNotRevokeMembers() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        group.setActive(false); // currently inactive → activating grants, no revoke
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenReturn(group);
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.update(id, new GroupRequest("Engineers", "desc", true));

        verify(sessionRevocationService, never()).revokeGroupMembers(any());
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

    private Group groupFixture(UUID id, String name) {
        Group group = new Group();
        group.setId(id);
        group.setName(name);
        group.setActive(true);
        return group;
    }
}