package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AssignMembersRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.GroupRequest;
import com.ibrhalil.forgesys.dto.GroupResponse;
import com.ibrhalil.forgesys.dto.RoleSummary;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;

    @Transactional(readOnly = true)
    public Page<GroupResponse> findAll(Pageable pageable) {
        return groupRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public GroupResponse findById(UUID id) {
        return toResponse(getGroupOrThrow(id));
    }

    @Transactional
    public GroupResponse create(GroupRequest request) {
        if (groupRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.GROUP_NAME_TAKEN, "Group name already exists: " + request.name());
        }
        Group group = new Group();
        group.setName(request.name());
        group.setDescription(request.description());
        group.setActive(request.active() == null || request.active());
        Group saved = groupRepository.save(group);
        auditService.record("group_created", "Group", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public GroupResponse update(UUID id, GroupRequest request) {
        Group group = getGroupOrThrow(id);
        if (!group.getName().equals(request.name()) && groupRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.GROUP_NAME_TAKEN, "Group name already exists: " + request.name());
        }
        boolean wasActive = group.isActive();
        group.setName(request.name());
        group.setDescription(request.description());
        if (request.active() != null) {
            group.setActive(request.active());
        }
        Group saved = groupRepository.save(group);
        // Faz 1: deactivating a group drops every member's group-granted permissions
        // (resolveAuthorities skips inactive groups) — revoke members so the loss is
        // enforced immediately. Activation grants permissions members gain on their next
        // login, so it needs no revoke.
        if (wasActive && Boolean.FALSE.equals(request.active())) {
            sessionRevocationService.revokeGroupMembers(saved.getId());
        }
        auditService.record("group_updated", "Group", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!groupRepository.existsById(id)) {
            throw new ResourceNotFoundException("Group not found: " + id);
        }
        // Faz 1: revoke BEFORE the soft-delete. findUserIdsByGroup joins through the group
        // entity, which @SQLRestriction filters out once is_deleted=true — resolving
        // members after deleteById would return nobody. Members lose the group's roles on
        // deletion, so kill their sessions now.
        sessionRevocationService.revokeGroupMembers(id);
        groupRepository.deleteById(id);
        auditService.record("group_deleted", "Group", id, null);
    }

    @Transactional
    public GroupResponse setRoles(UUID groupId, AssignRolesRequest request) {
        Group group = getGroupOrThrow(groupId);
        List<Role> roles = resolveRoles(request.roleIds());
        java.util.Set<String> beforeNames = group.getRoles().stream()
                .map(Role::getName).collect(java.util.stream.Collectors.toSet());
        group.getRoles().clear();
        group.getRoles().addAll(roles);
        Group saved = groupRepository.save(group);
        // Faz 1: a role delta on this group changes every member's effective permissions,
        // but their outstanding tokens still embed the old set — revoke members so the
        // delta is enforced on the next request, not at access-token TTL.
        sessionRevocationService.revokeGroupMembers(saved.getId());
        // Faz 2b: record the before/after role set on the group.
        auditService.recordDelta("group_roles_updated", "Group", saved.getId(), saved.getName(),
                AuditService.namesJson(beforeNames),
                AuditService.namesJson(roles.stream().map(Role::getName).collect(java.util.stream.Collectors.toSet())));
        return toResponse(saved);
    }

    /**
     * Replace-semantics membership update. Since {@code User} owns the {@code t_user_groups}
     * join, this mutates each affected user's group set: removes the group from users no
     * longer targeted, adds it to newly targeted users.
     */
    @Transactional
    public GroupResponse setMembers(UUID groupId, AssignMembersRequest request) {
        Group group = getGroupOrThrow(groupId);
        Set<UUID> targetIds = new LinkedHashSet<>(request.userIds());
        List<User> targetUsers = targetIds.isEmpty()
                ? List.of()
                : userRepository.findAllById(targetIds);
        if (targetUsers.size() != targetIds.size()) {
            throw new ResourceNotFoundException("One or more users not found");
        }

        Set<UUID> removedMemberIds = new LinkedHashSet<>();
        for (User current : userRepository.findGroupMembers(groupId)) {
            if (!targetIds.contains(current.getId()) && current.getGroups().remove(group)) {
                userRepository.save(current);
                removedMemberIds.add(current.getId());
            }
        }
        for (User target : targetUsers) {
            if (target.getGroups().add(group)) {
                userRepository.save(target);
            }
        }
        // Faz 1: only REMOVED members lose the group's permissions (the security-relevant
        // case). Added members gain permissions on their next login — no revoke, so adding
        // someone to a group does not log them out.
        sessionRevocationService.revokeUsers(removedMemberIds);
        auditService.record("group_members_updated", "Group", group.getId(), group.getName());
        return toResponse(group);
    }

    private List<Role> resolveRoles(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> distinctIds = new LinkedHashSet<>(roleIds);
        List<Role> roles = roleRepository.findAllById(distinctIds);
        if (roles.size() != distinctIds.size()) {
            throw new ResourceNotFoundException("One or more roles not found");
        }
        return roles;
    }

    private Group getGroupOrThrow(UUID id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + id));
    }

    private GroupResponse toResponse(Group group) {
        List<RoleSummary> roles = group.getRoles().stream()
                .map(role -> new RoleSummary(role.getId(), role.getName()))
                .sorted(Comparator.comparing(RoleSummary::name))
                .toList();
        return new GroupResponse(
                group.getId(), group.getName(), group.getDescription(), group.isActive(),
                roles, groupRepository.countMembers(group.getId()));
    }
}
