package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AssignMembersRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.GroupRequest;
import com.ibrhalil.forgesys.dto.GroupResponse;
import com.ibrhalil.forgesys.dto.RoleSummary;
import com.ibrhalil.forgesys.dto.UserSummary;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Group_;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.audit.AuditDeltaContext;
import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import com.ibrhalil.forgesys.security.CustomUserDetailsService;
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
public class GroupService {

    /** Filterable/sortable direct attributes of the group list; {@code q} matches {@code name}. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Group_.NAME, FilterFieldType.STRING, true)
            .field(Group_.DESCRIPTION, FilterFieldType.STRING, false)
            .field(Group_.ACTIVE, FilterFieldType.BOOLEAN, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final GroupRepository groupRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;
    private final CustomUserDetailsService customUserDetailsService;
    private final LastAdminGuard lastAdminGuard;

    @Transactional(readOnly = true)
    public Page<GroupResponse> search(String q, Pageable pageable) {
        Specification<Group> spec = FilterSpecifications.from(FILTER_FIELDS, StringUtils.hasText(q) ? q.trim() : null, List.of());
        return groupRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public GroupResponse findById(UUID id) {
        return toResponse(getGroupOrThrow(id));
    }

    /**
     * Sorted effective permission names this group grants its members: the union of the
     * permissions of every role the group holds, expanded through transitive parent-role
     * inheritance. Backs {@code GET /groups/{id}/effective-permissions} so the UI can
     * show what a member of this group can do (a group carrying an admin role makes its
     * members admins).
     */
    @Transactional(readOnly = true)
    public List<String> effectivePermissions(UUID id) {
        Group group = getGroupOrThrow(id);
        Set<UUID> roleIds = group.getRoles().stream().map(Role::getId).collect(java.util.stream.Collectors.toSet());
        return customUserDetailsService.resolvePermissionNames(roleIds);
    }

    @Transactional
    @AuditLog(action = "group_created", entityType = "Group", entityId = "#result.id", entityName = "#result.name")
    public GroupResponse create(GroupRequest request) {
        if (groupRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.GROUP_NAME_TAKEN, "Group name already exists: " + request.name());
        }
        Group group = new Group();
        group.setName(request.name());
        group.setDescription(request.description());
        group.setActive(request.active() == null || request.active());
        Group saved = groupRepository.save(group);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "group_updated", entityType = "Group", entityId = "#result.id", entityName = "#result.name")
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
        // Last-admin guard: deactivating the group immediately strips its members'
        // group-granted admin capacity — the existence query auto-flushes the pending
        // active=false and skips inactive groups, so this sees the post-mutation state.
        if (wasActive && Boolean.FALSE.equals(request.active())) {
            lastAdminGuard.assertActiveAdminExists();
        }
        Group saved = groupRepository.save(group);
        // Faz 1: deactivating a group drops every member's group-granted permissions
        // (resolveAuthorities skips inactive groups) — revoke members so the loss is
        // enforced immediately. Activation grants permissions members gain on their next
        // login, so it needs no revoke.
        if (wasActive && Boolean.FALSE.equals(request.active())) {
            sessionRevocationService.revokeGroupMembers(saved.getId());
        }
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "group_deleted", entityType = "Group", entityId = "#id", entityName = "")
    public void delete(UUID id) {
        Group group = getGroupOrThrow(id);
        // Detach the group from every member's collection BEFORE the soft-delete:
        // t_user_groups is owned by User.groups, and leaving the join rows behind keeps
        // managed collections referencing a deleted group (flush failure with
        // TransientPropertyValueException) plus orphan rows.
        List<UUID> memberIds = new java.util.ArrayList<>();
        for (User member : userRepository.findGroupMembers(id)) {
            if (member.getGroups().remove(group)) {
                memberIds.add(member.getId());
            }
        }
        groupRepository.delete(group);
        // Last-admin guard AFTER the soft-delete (auto-flushed): the deleted group is
        // invisible to the existence query, so deleting the last admin-carrying group
        // is rejected and the whole tx rolls back. The revoke fires only after the
        // guard, so a rejected delete leaves no Redis-side revoke behind.
        lastAdminGuard.assertActiveAdminExists();
        sessionRevocationService.revokeUsers(memberIds);
    }

    @Transactional
    @AuditLog(action = "group_roles_updated", entityType = "Group", entityId = "#result.id", entityName = "#result.name",
            captureDelta = true)
    public GroupResponse setRoles(UUID groupId, AssignRolesRequest request) {
        Group group = getGroupOrThrow(groupId);
        List<Role> roles = resolveRoles(request.roleIds());
        java.util.Set<String> beforeNames = group.getRoles().stream()
                .map(Role::getName).collect(java.util.stream.Collectors.toSet());
        group.getRoles().clear();
        group.getRoles().addAll(roles);
        // Last-admin guard: stripping the group's admin-carrying role drops every
        // member's group-granted admin capacity — auto-flushed before the check.
        lastAdminGuard.assertActiveAdminExists();
        Group saved = groupRepository.save(group);
        // Faz 1: a role delta on this group changes every member's effective permissions,
        // but their outstanding tokens still embed the old set — revoke members so the
        // delta is enforced on the next request, not at access-token TTL.
        sessionRevocationService.revokeGroupMembers(saved.getId());
        // Faz 2b: set delta values for AOP aspect
        AuditDeltaContext.setOldValue(AuditService.namesJson(beforeNames));
        AuditDeltaContext.setNewValue(AuditService.namesJson(roles.stream().map(Role::getName).collect(java.util.stream.Collectors.toSet())));
        return toResponse(saved);
    }

    /**
     * Replace-semantics membership update. Since {@code User} owns the {@code t_user_groups}
     * join, this mutates each affected user's group set: removes the group from users no
     * longer targeted, adds it to newly targeted users.
     */
    @Transactional
    @AuditLog(action = "group_members_updated", entityType = "Group", entityId = "#group.id", entityName = "#group.name")
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
        // Last-admin guard: removing the last admin from an admin-carrying group drops
        // the tenant below the one-active-admin floor — the existence query
        // auto-flushes the pending t_user_groups removals before running.
        lastAdminGuard.assertActiveAdminExists();
        // Faz 1: only REMOVED members lose the group's permissions (the security-relevant
        // case). Added members gain permissions on their next login — no revoke, so adding
        // someone to a group does not log them out.
        sessionRevocationService.revokeUsers(removedMemberIds);
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
        List<UserSummary> members = userRepository.findGroupMembers(group.getId()).stream()
                .map(u -> new UserSummary(u.getId(), u.getEmail()))
                .sorted(Comparator.comparing(UserSummary::email))
                .toList();
        return new GroupResponse(
                group.getId(), group.getName(), group.getDescription(), group.isActive(),
                roles, members, groupRepository.countMembers(group.getId()));
    }
}
