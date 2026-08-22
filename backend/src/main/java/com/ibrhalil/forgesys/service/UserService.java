package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AssignGroupsRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.AdminPasswordResetRequest;
import com.ibrhalil.forgesys.dto.GroupSummary;
import com.ibrhalil.forgesys.dto.PasswordChangeRequest;
import com.ibrhalil.forgesys.dto.RoleSummary;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.dto.UserCreateRequest;
import com.ibrhalil.forgesys.dto.UserActivityResponse;
import com.ibrhalil.forgesys.dto.UserDirectoryViewResponse;
import com.ibrhalil.forgesys.dto.UserProfileUpdateRequest;
import com.ibrhalil.forgesys.dto.UserResponse;
import com.ibrhalil.forgesys.dto.UserUpdateRequest;
import com.ibrhalil.forgesys.config.PermissionCatalog;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserDirectoryView;
import com.ibrhalil.forgesys.entity.UserDirectoryView_;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.LoginHistoryRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserDirectoryViewRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import com.ibrhalil.forgesys.security.CustomUserDetailsService;
import com.ibrhalil.forgesys.audit.AuditDeltaContext;
import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.security.LastAdminGuard;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    /**
     * Filterable/sortable attributes of the user directory list. {@code q} matches
     * email, username, first and last name (the profile join in the directory subselect
     * finally makes name search possible). Also the sort whitelist for both
     * {@code GET /users} and {@code POST /users/search}.
     */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(UserDirectoryView_.EMAIL, FilterFieldType.STRING, true)
            .field(UserDirectoryView_.USERNAME, FilterFieldType.STRING, true)
            .field(UserDirectoryView_.FIRST_NAME, FilterFieldType.STRING, true)
            .field(UserDirectoryView_.LAST_NAME, FilterFieldType.STRING, true)
            .field(UserDirectoryView_.EMAIL_VERIFIED, FilterFieldType.BOOLEAN, false)
            .field(UserDirectoryView_.ENABLED, FilterFieldType.BOOLEAN, false)
            .field(UserDirectoryView_.LOCKED_UNTIL, FilterFieldType.TEMPORAL, false)
            .field(UserDirectoryView_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .build();

    private final UserRepository userRepository;
    private final UserDirectoryViewRepository userDirectoryViewRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final RoleRepository roleRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;
    private final CustomUserDetailsService customUserDetailsService;
    private final LastAdminGuard lastAdminGuard;

    @Transactional(readOnly = true)
    public Page<UserDirectoryViewResponse> search(String q, Pageable pageable) {
        Specification<UserDirectoryView> spec = FilterSpecifications.from(FILTER_FIELDS, StringUtils.hasText(q) ? q.trim() : null, List.of());
        return userDirectoryViewRepository.findAll(applyVisibilityScope(spec), pageable).map(this::toDirectoryResponse);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /users/search}. */
    @Transactional(readOnly = true)
    public Page<UserDirectoryViewResponse> search(SearchRequest request, Pageable pageable) {
        Specification<UserDirectoryView> spec = FilterSpecifications.from(FILTER_FIELDS, request.q(), request.filters());
        return userDirectoryViewRepository.findAll(applyVisibilityScope(spec), pageable).map(this::toDirectoryResponse);
    }

    // ── visibility scope (iam:user:read vs iam:group-member:read) ──

    /**
     * Narrows a directory {@link Specification} to what the caller may see:
     * {@code iam:user:read} holders are unrestricted; callers holding only
     * {@code iam:group-member:read} see the members of their own groups plus
     * themselves. Tenant-schema isolation is untouched — this is row-level visibility
     * inside the tenant, resolved as one extra {@code IN} predicate in the same query.
     */
    private Specification<UserDirectoryView> applyVisibilityScope(Specification<UserDirectoryView> spec) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return spec;
        }
        if (hasAuthority(authentication, PermissionCatalog.IAM_USER_READ)) {
            return spec;
        }
        UUID callerId = callerId(authentication);
        Set<UUID> visible = new HashSet<>(visibleUserIds(callerId));
        return spec.and((root, query, cb) ->
                cb.or(cb.equal(root.get(UserDirectoryView_.ID), callerId),
                        visible.isEmpty() ? cb.disjunction() : root.get(UserDirectoryView_.ID).in(visible)));
    }

    /**
     * Detail-scope guard: throws 403 {@code auth_access_denied} when the caller (who by
     * construction lacks {@code iam:user:read}) is neither the target, a member of one
     * of the target's groups, nor the target itself. Applied to {@code findById} and
     * {@code effectivePermissions}; self-service {@code /users/me} passes trivially
     * (self is always in scope).
     */
    private void assertViewable(UUID targetId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || hasAuthority(authentication, PermissionCatalog.IAM_USER_READ)) {
            return;
        }
        UUID callerId = callerId(authentication);
        if (callerId.equals(targetId) || visibleUserIds(callerId).contains(targetId)) {
            return;
        }
        throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED,
                "You may only view members of your own groups");
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private UUID callerId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "Unrecognized principal");
    }

    /** Members of the caller's groups (self appended by the callers where needed). */
    private Collection<UUID> visibleUserIds(UUID callerId) {
        List<UUID> groupIds = userRepository.findGroupIdsByUserId(callerId);
        return groupIds.isEmpty() ? List.of() : userRepository.findUserIdsByGroupIds(groupIds);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        assertViewable(id);
        return toResponse(getUserOrThrow(id));
    }

    /**
     * Sorted effective permission names for a user: direct roles + active-group roles +
     * transitive parent inheritance, resolved to permission wire strings. Backs
     * {@code GET /users/{id}/effective-permissions} so the UI can surface what the user
     * can actually do (including group-granted / inherited authority). Scope-guarded
     * like the detail endpoint.
     */
    @Transactional(readOnly = true)
    public List<String> effectivePermissions(UUID id) {
        assertViewable(id);
        getUserOrThrow(id);
        return customUserDetailsService.resolveEffectivePermissionNamesForUser(id);
    }

    /**
     * Temporal account-activity summary for the user detail view: audit stamps from
     * the user entity, last login from the account row, and the latest failed attempt
     * from the append-only login history (K-19). Scope-guarded like the detail
     * endpoint. One extra indexed single-row query — kept OUT of {@code toResponse}
     * so mutation responses don't pay for it.
     */
    @Transactional(readOnly = true)
    public UserActivityResponse activity(UUID id) {
        assertViewable(id);
        User user = getUserOrThrow(id);
        UserAccount account = user.getUserAccount();
        OffsetDateTime lastFailed = loginHistoryRepository
                .findFirstByUserIdAndSuccessFalseOrderByCreatedDateDesc(id)
                .map(entity -> entity.getCreatedDate())
                .orElse(null);
        return new UserActivityResponse(
                user.getCreatedDate(),
                user.getCreatedBy(),
                user.getUpdatedAt(),
                user.getUpdatedBy(),
                account == null ? null : account.getLastLoginAt(),
                lastFailed);
    }

    @Transactional
    @AuditLog(action = "user_created", entityType = "User", entityId = "#result.id", entityName = "#result.email")
    public UserResponse create(UserCreateRequest request) {
        String username = resolveUsername(request.username(), request.email());
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.USER_EMAIL_TAKEN, "Email already exists: " + request.email());
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USER_USERNAME_TAKEN, "Username already exists: " + username);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmailVerified(false);

        UserAccount account = new UserAccount();
        account.setUser(user);
        account.setEnabled(request.enabled() == null || request.enabled());
        user.setUserAccount(account);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        user.setUserProfile(profile);

        User saved = userRepository.save(user);
        // Optional roles/groups assigned at creation. No session revoke needed — the user
        // has no outstanding tokens yet. Validates ids up front (404 if any are unknown).
        List<Role> roles = resolveRoles(request.roleIds());
        if (!roles.isEmpty()) {
            user.getRoles().addAll(roles);
        }
        List<Group> groups = resolveGroups(request.groupIds());
        if (!groups.isEmpty()) {
            user.getGroups().addAll(groups);
        }
        if (!roles.isEmpty() || !groups.isEmpty()) {
            userRepository.save(user);
        }
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "user_updated", entityType = "User", entityId = "#result.id", entityName = "#result.email")
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = getUserOrThrow(id);
        UserProfile profile = user.getUserProfile();
        if (profile != null) {
            profile.setFirstName(request.firstName());
            profile.setLastName(request.lastName());
        }
        boolean disabling = request.enabled() != null && !request.enabled() && user.getUserAccount().isEnabled();
        if (request.enabled() != null) {
            user.getUserAccount().setEnabled(request.enabled());
        }
        // Last-admin guard BEFORE save: the JPQL existence check auto-flushes the
        // pending enabled=false, so this sees the post-mutation state. Re-enable and
        // no-op toggles fall through (guard is a no-op when an admin remains).
        if (disabling) {
            lastAdminGuard.assertActiveAdminExists();
        }
        User saved = userRepository.save(user);
        // Side-fix 2: a disabled user's outstanding tokens must die now, not at TTL
        // (login-side enable check is [side-fix 1]; the JWT filter does not re-read
        // account flags per request).
        if (disabling) {
            sessionRevocationService.revokeUser(saved.getId());
        }
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "user_deleted", entityType = "User", entityId = "#id", entityName = "")
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        // Self-delete is forbidden unconditionally — the historical cause of tenant
        // lockouts (admin soft-deleting themselves left zero admins).
        lastAdminGuard.assertNotSelf(id);
        userRepository.deleteById(id);
        // Last-admin check AFTER the soft-delete flush — the existence query sees the
        // post-delete state; on violation the whole tx rolls back (delete undone) and
        // the revoke below never fires (no Redis-side session carnage on rejection).
        lastAdminGuard.assertActiveAdminExists();
        // Side-fix 2: the deleted user's outstanding tokens must die now, not at TTL
        // (the bulk stamp targets the row directly, independent of the soft-delete).
        sessionRevocationService.revokeUser(id);
    }

    /**
     * Clears an active brute-force lockout ([RISK-22]) ahead of its expiry: resets the
     * failed-attempt counter and clears {@code lockedUntil}. The lock leaves refresh
     * tokens in place (they work again once unlocked), so no session revive is needed —
     * the user can log in immediately. No-op state-wise for an account that is not
     * currently locked (still audited).
     */
    @Transactional
    @AuditLog(action = "user_unlocked", entityType = "User", entityId = "#result.id", entityName = "#result.email")
    public UserResponse unlock(UUID id) {
        User user = getUserOrThrow(id);
        UserAccount account = user.getUserAccount();
        account.setLockedUntil(null);
        account.setFailedLoginAttempts(0);
        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "user_roles_updated", entityType = "User", entityId = "#result.id", entityName = "#result.email",
            captureDelta = true)
    public UserResponse setRoles(UUID userId, AssignRolesRequest request) {
        User user = getUserOrThrow(userId);
        List<Role> roles = resolveRoles(request.roleIds());
        Set<String> beforeNames = user.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet());
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        // Last-admin guard: stripping the target's admin-carrying role may drop the
        // tenant below one active admin — the existence query auto-flushes the join-row
        // removal, so this sees the post-mutation closure.
        lastAdminGuard.assertActiveAdminExists();
        User saved = userRepository.save(user);
        // Faz 1: a role-set change can drop permissions the user's outstanding tokens
        // still carry — kill their sessions so the delta is enforced immediately, not at
        // the next access-token TTL.
        sessionRevocationService.revokeUser(saved.getId());
        // Faz 2b: set delta values for AOP aspect
        AuditDeltaContext.setOldValue(AuditService.namesJson(beforeNames));
        AuditDeltaContext.setNewValue(AuditService.namesJson(roles.stream().map(Role::getName).collect(java.util.stream.Collectors.toSet())));
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "user_groups_updated", entityType = "User", entityId = "#result.id", entityName = "#result.email",
            captureDelta = true)
    public UserResponse setGroups(UUID userId, AssignGroupsRequest request) {
        User user = getUserOrThrow(userId);
        List<Group> groups = resolveGroups(request.groupIds());
        Set<String> beforeNames = user.getGroups().stream().map(Group::getName).collect(java.util.stream.Collectors.toSet());
        user.getGroups().clear();
        user.getGroups().addAll(groups);
        // Last-admin guard: removing the user from an admin-carrying group may drop
        // the tenant below one active admin (auto-flushed before the check).
        lastAdminGuard.assertActiveAdminExists();
        User saved = userRepository.save(user);
        // Faz 1: a group-set change can drop group-granted permissions the user's
        // outstanding tokens still carry — kill their sessions immediately.
        sessionRevocationService.revokeUser(saved.getId());
        // Faz 2b: set delta values for AOP aspect
        AuditDeltaContext.setOldValue(AuditService.namesJson(beforeNames));
        AuditDeltaContext.setNewValue(AuditService.namesJson(groups.stream().map(Group::getName).collect(java.util.stream.Collectors.toSet())));
        return toResponse(saved);
    }

    /**
     * Self-service profile update. Patch semantics: only non-null request fields are
     * applied (null = leave unchanged); an empty string clears a field.
     */
    @Transactional
    public UserResponse updateProfile(UUID userId, UserProfileUpdateRequest request) {
        User user = getUserOrThrow(userId);
        UserProfile profile = user.getUserProfile();
        if (profile == null) {
            profile = new UserProfile();
            profile.setUser(user);
            user.setUserProfile(profile);
        }
        if (request.firstName() != null) profile.setFirstName(request.firstName());
        if (request.lastName() != null) profile.setLastName(request.lastName());
        if (request.phoneNumber() != null) profile.setPhoneNumber(request.phoneNumber());
        if (request.address() != null) profile.setAddress(request.address());
        if (request.city() != null) profile.setCity(request.city());
        if (request.country() != null) profile.setCountry(request.country());
        if (request.zipCode() != null) profile.setZipCode(request.zipCode());
        return toResponse(userRepository.save(user));
    }

    /**
     * Self-service password change. Verifies the current password before applying the
     * new one, then stamps {@code tokenInvalidBefore = now()} so every access token
     * issued before this change is rejected by {@code JwtAuthenticationFilter}
     * ([RISK-21]). Multi-device logout side effect: ALL of the user's outstanding
     * sessions are killed, not just the current one. Granular (single-session) revoke
     * arrives with Redis-backed blacklist (Epic 2.6).
     */
    @Transactional
    @AuditLog(action = "user_password_changed", entityType = "User", entityId = "#userId", entityName = "#user.email")
    public void changePassword(UUID userId, PasswordChangeRequest request) {
        User user = getUserOrThrow(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_INCORRECT);
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        invalidateTokens(user);
        userRepository.save(user);
    }

    /**
     * Admin-issued password reset. No current password is verified — the caller already
     * holds {@code iam:user:write}. {@code tokenInvalidBefore = now()} stamps the reset
     * time so the user's previous tokens (if any) no longer authenticate
     * ([RISK-21] — same multi-device logout note as {@link #changePassword}).
     */
    @Transactional
    @AuditLog(action = "user_password_reset", entityType = "User", entityId = "#userId", entityName = "#user.email")
    public void resetPassword(UUID userId, AdminPasswordResetRequest request) {
        User user = getUserOrThrow(userId);
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        invalidateTokens(user);
        userRepository.save(user);
    }

    /**
     * [RISK-21] Logout hook: stamps {@code tokenInvalidBefore = now()} for the user.
     * Called by {@code AuthController.logout} with the authenticated principal's id.
     * Revokes every outstanding access token for the user (multi-device logout);
     * granular per-session revoke is deferred to Epic 2.6 (Redis blacklist). The cookie
     * is also expired client-side by the controller so the browser drops it.
     */
    @Transactional
    public void revokeTokens(UUID userId) {
        User user = getUserOrThrow(userId);
        invalidateTokens(user);
        userRepository.save(user);
    }

    /**
     * [RISK-21 + K-34 + Faz 1] Centralized session revoke for this user: delegates to
     * {@link SessionRevocationService#revokeUser(UUID)}, which stamps
     * {@code tokenInvalidBefore} (kills all outstanding access tokens) and drops the
     * user's refresh tokens (so a stolen refresh cannot mint a fresh access token whose
     * {@code iat} post-dates the revoke). Invoked on password change/reset and explicit
     * token revoke — multi-device, all sessions.
     */
    private void invalidateTokens(User user) {
        sessionRevocationService.revokeUser(user.getId());
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

    private List<Group> resolveGroups(List<UUID> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> distinctIds = new LinkedHashSet<>(groupIds);
        List<Group> groups = groupRepository.findAllById(distinctIds);
        if (groups.size() != distinctIds.size()) {
            throw new ResourceNotFoundException("One or more groups not found");
        }
        return groups;
    }

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private UserDirectoryViewResponse toDirectoryResponse(UserDirectoryView d) {
        return new UserDirectoryViewResponse(
                d.getId(),
                d.getUsername(),
                d.getEmail(),
                d.isEmailVerified(),
                d.getFirstName(),
                d.getLastName(),
                d.isEnabled(),
                d.getLockedUntil(),
                d.getLastLoginAt(),
                d.getCreatedDate(),
                d.getRoleCount(),
                d.getGroupCount());
    }

    private String resolveUsername(String username, String email) {
        if (username != null && !username.isBlank()) {
            return username;
        }
        int at = email.indexOf('@');
        String prefix = at > 0 ? email.substring(0, at) : email;
        return prefix.length() > 70 ? prefix.substring(0, 70) : prefix;
    }

    private UserResponse toResponse(User user) {
        List<RoleSummary> roles = user.getRoles().stream()
                .map(role -> new RoleSummary(role.getId(), role.getName()))
                .sorted(Comparator.comparing(RoleSummary::name))
                .toList();
        List<GroupSummary> groups = user.getGroups().stream()
                .map(group -> new GroupSummary(group.getId(), group.getName()))
                .sorted(Comparator.comparing(GroupSummary::name))
                .toList();
        UserProfile profile = user.getUserProfile();
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getUserAccount().isEnabled(),
                user.getUserAccount().getLockedUntil(),
                profile == null ? null : profile.getFirstName(),
                profile == null ? null : profile.getLastName(),
                profile == null ? null : profile.getPhoneNumber(),
                profile == null ? null : profile.getAddress(),
                profile == null ? null : profile.getCity(),
                profile == null ? null : profile.getCountry(),
                profile == null ? null : profile.getZipCode(),
                roles,
                groups);
    }
}
