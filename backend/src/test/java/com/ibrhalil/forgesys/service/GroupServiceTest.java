package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AssignMembersRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.GroupRequest;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository, roleRepository, userRepository, auditService);
    }

    @Test
    void createRecordsAudit() {
        when(groupRepository.existsByName("Engineers")).thenReturn(false);
        UUID id = UUID.randomUUID();
        when(groupRepository.save(any(Group.class))).thenReturn(groupFixture(id, "Engineers"));
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.create(new GroupRequest("Engineers", "desc", true));

        verify(auditService).record("group_created", "Group", id, "Engineers");
    }

    @Test
    void updateRecordsAudit() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenReturn(group);
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.update(id, new GroupRequest("Engineers", "desc", true));

        verify(auditService).record("group_updated", "Group", id, "Engineers");
    }

    @Test
    void deleteRecordsAudit() {
        UUID id = UUID.randomUUID();
        when(groupRepository.existsById(id)).thenReturn(true);

        groupService.delete(id);

        verify(groupRepository).deleteById(id);
        verify(auditService).record("group_deleted", "Group", id, null);
    }

    @Test
    void setRolesRecordsAudit() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenReturn(group);
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.setRoles(id, new AssignRolesRequest(List.of()));

        verify(auditService).record("group_roles_updated", "Group", id, "Engineers");
    }

    @Test
    void setMembersRecordsAudit() {
        UUID id = UUID.randomUUID();
        Group group = groupFixture(id, "Engineers");
        when(groupRepository.findById(id)).thenReturn(Optional.of(group));
        when(userRepository.findGroupMembers(id)).thenReturn(List.of());
        when(groupRepository.countMembers(id)).thenReturn(0L);

        groupService.setMembers(id, new AssignMembersRequest(List.of()));

        verify(auditService).record("group_members_updated", "Group", id, "Engineers");
    }

    private Group groupFixture(UUID id, String name) {
        Group group = new Group();
        group.setId(id);
        group.setName(name);
        group.setActive(true);
        return group;
    }
}
