package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AssignMembersRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.GroupRequest;
import com.ibrhalil.forgesys.dto.GroupResponse;
import com.ibrhalil.forgesys.dto.RoleSummary;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.dto.UserSummary;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Group_;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.User_;
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

    /**
     * Filterable/sortable attributes of the group list (K-49); {@code q} matches
     * {@code name}/{@code description}. {@code memberIds} resolves through the inverse
     * {@code User.groups} side (the join table is owned by User).
     */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Group_.NAME, FilterFieldType.STRING, true)
            .field(Group_.DESCRIPTION, FilterFieldType.STRING, true)
            .field(Group_.ACTIVE, FilterFieldType.BOOLEAN, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .subqueryField("roleCount", FilterFieldType.NUMERIC, false, GroupListQueryExecutor.countRoles())
            .membershipField("roleIds", Group_.ROLES, BaseEntity_.ID)
            .subqueryField("memberCount", FilterFieldType.NUMERIC, false, GroupListQueryExecutor.countMembers())
            .inverseMembershipField("memberIds", User.class, User_.GROUPS, BaseEntity_.ID, BaseEntity_.ID)
            .build();

    private final GroupRepository groupRepository;
    private final GroupListQueryExecutor groupListQueryExecutor;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;
    private final CustomUserDetailsService customUserDetailsService;
    private final LastAdminGuard lastAdminGuard;

    @Transactional(readOnly = true)
    public Page<GroupResponse> search(String q, List<String> qFields, Pageable pageable) {
        Specification<Group> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, qFields, List.of());
        return groupListQueryExecutor.search(spec, pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /groups/search}. */
    @Transactional(readOnly = true)
    public Page<GroupResponse> search(SearchRequest request, Pageable pageable) {
        Specification<Group> spec = FilterSpecifications.from(FILTER_FIELDS, request.q(), request.qFields(),
                request.filters());
        return groupListQueryExecutor.search(spec, pageable);
    }

    @Transactional(readOnly = true)
    public GroupResponse findById(UUID id) {
        return toResponse(getGroupOrThrow(id));
    }

    /**
     * Effective permission names the group grants its members (union of the group's
     * roles, expanded through transitive parent inheritance).
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
        // Guard: deactivation strips members' group-granted admin capacity (the
        // existence query auto-flushes the pending active=false first).
        if (wasActive && Boolean.FALSE.equals(request.active())) {
            lastAdminGuard.assertActiveAdminExists();
        }
        Group saved = groupRepository.save(group);
        // Deactivation drops members' permissions (resolveAuthorities skips inactive
        // groups) → revoke; activation grants at next login, no revoke.
        if (wasActive && Boolean.FALSE.equals(request.active())) {
            sessionRevocationService.revokeGroupMembers(saved.getId());
        }
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "group_deleted", entityType = "Group", entityId = "#id", entityName = "")
    public void delete(UUID id) {
        Group group = getGroupOrThrow(id);
        // Detach from every member's collection BEFORE the soft-delete — leftover join
        // rows fail the flush (TransientPropertyValueException) and orphan rows.
        List<UUID> memberIds = new java.util.ArrayList<>();
        for (User member : userRepository.findGroupMembers(id)) {
            if (member.getGroups().remove(group)) {
                memberIds.add(member.getId());
            }
        }
        groupRepository.delete(group);
        // Guard AFTER the soft-delete flush, BEFORE the revoke — a rejection rolls back
        // the tx without leaving Redis-side carnage behind.
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
        // Guard: stripping the admin-carrying role drops members' admin capacity (auto-flushed first).
        lastAdminGuard.assertActiveAdminExists();
        Group saved = groupRepository.save(group);
        // Revoke members: their outstanding tokens still embed the old permission set.
        sessionRevocationService.revokeGroupMembers(saved.getId());
        // Faz 2b: set delta values for AOP aspect
        AuditDeltaContext.setOldValue(AuditService.namesJson(beforeNames));
        AuditDeltaContext.setNewValue(AuditService.namesJson(roles.stream().map(Role::getName).collect(java.util.stream.Collectors.toSet())));
        return toResponse(saved);
    }

    /** Replace-semantics membership update; mutates each user's group set (User owns the join). */
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
        // Guard: removing the last admin from an admin-carrying group drops below the
        // floor (the existence query auto-flushes the pending removals first).
        lastAdminGuard.assertActiveAdminExists();
        // Only REMOVED members lose permissions — added members gain at next login
        // (adding someone to a group must not log them out).
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
