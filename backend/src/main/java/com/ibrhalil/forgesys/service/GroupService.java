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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

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
        return toResponse(groupRepository.save(group));
    }

    @Transactional
    public GroupResponse update(UUID id, GroupRequest request) {
        Group group = getGroupOrThrow(id);
        if (!group.getName().equals(request.name()) && groupRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.GROUP_NAME_TAKEN, "Group name already exists: " + request.name());
        }
        group.setName(request.name());
        group.setDescription(request.description());
        if (request.active() != null) {
            group.setActive(request.active());
        }
        return toResponse(groupRepository.save(group));
    }

    @Transactional
    public void delete(UUID id) {
        if (!groupRepository.existsById(id)) {
            throw new ResourceNotFoundException("Group not found: " + id);
        }
        groupRepository.deleteById(id);
    }

    @Transactional
    public GroupResponse setRoles(UUID groupId, AssignRolesRequest request) {
        Group group = getGroupOrThrow(groupId);
        List<Role> roles = resolveRoles(request.roleIds());
        group.getRoles().clear();
        group.getRoles().addAll(roles);
        return toResponse(groupRepository.save(group));
    }

    /**
     * Replace-semantics membership update. Since {@code User} owns the {@code t_user_groups}
     * join, this mutates each affected user's group set: removes the group from users no
     * longer targeted, adds it to newly targeted users.
     */
    @Transactional
    public GroupResponse setMembers(UUID groupId, AssignMembersRequest request) {
        Group group = getGroupOrThrow(groupId);
        List<User> targetUsers = request.userIds().isEmpty()
                ? List.of()
                : userRepository.findAllById(request.userIds());
        if (targetUsers.size() != request.userIds().size()) {
            throw new ResourceNotFoundException("One or more users not found");
        }
        Set<UUID> targetIds = new HashSet<>(request.userIds());

        for (User current : userRepository.findGroupMembers(groupId)) {
            if (!targetIds.contains(current.getId()) && current.getGroups().remove(group)) {
                userRepository.save(current);
            }
        }
        for (User target : targetUsers) {
            if (target.getGroups().add(group)) {
                userRepository.save(target);
            }
        }
        return toResponse(group);
    }

    private List<Role> resolveRoles(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
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
