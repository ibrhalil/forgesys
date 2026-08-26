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
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.User_;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserAccount_;
import com.ibrhalil.forgesys.entity.UserAuthToken;
import com.ibrhalil.forgesys.entity.UserAuthTokenPurpose;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.entity.UserProfile_;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.LoginHistoryRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import com.ibrhalil.forgesys.security.CustomUserDetailsService;
import com.ibrhalil.forgesys.service.mail.MailLinkBuilder;
import com.ibrhalil.forgesys.service.mail.MailMessage;
import com.ibrhalil.forgesys.service.mail.MailSender;
import com.ibrhalil.forgesys.service.mail.MailTemplate;
import com.ibrhalil.forgesys.audit.AuditDeltaContext;
import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.security.LastAdminGuard;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /**
     * Filterable/sortable attributes of the user directory list (K-49) — also the sort
     * whitelist for {@code GET /users} and {@code POST /users/search}. {@code q} matches
     * email, username, first and last name. Rationale: docs/CODE_NOTES.md (backend/service → UserService).
     */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(User_.EMAIL, FilterFieldType.STRING, true)
            .field(User_.USERNAME, FilterFieldType.STRING, true)
            .joinedField("firstName", FilterFieldType.STRING, true, User_.USER_PROFILE, UserProfile_.FIRST_NAME)
            .joinedField("lastName", FilterFieldType.STRING, true, User_.USER_PROFILE, UserProfile_.LAST_NAME)
            .field(User_.EMAIL_VERIFIED, FilterFieldType.BOOLEAN, false)
            .joinedField("enabled", FilterFieldType.BOOLEAN, false, User_.USER_ACCOUNT, UserAccount_.ENABLED)
            .joinedField("lockedUntil", FilterFieldType.TEMPORAL, false, User_.USER_ACCOUNT, UserAccount_.LOCKED_UNTIL)
            .joinedField("lastLoginAt", FilterFieldType.TEMPORAL, false, User_.USER_ACCOUNT, UserAccount_.LAST_LOGIN_AT)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .subqueryField("roleCount", FilterFieldType.NUMERIC, false, UserDirectoryQueryExecutor.countMembers(User_.ROLES))
            .subqueryField("groupCount", FilterFieldType.NUMERIC, false, UserDirectoryQueryExecutor.countMembers(User_.GROUPS))
            .membershipField("roleIds", User_.ROLES, BaseEntity_.ID)
            .membershipField("groupIds", User_.GROUPS, BaseEntity_.ID)
            .build();

    private final UserRepository userRepository;
    private final UserDirectoryQueryExecutor userDirectoryQueryExecutor;
    private final LoginHistoryRepository loginHistoryRepository;
    private final RoleRepository roleRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;
    private final CustomUserDetailsService customUserDetailsService;
    private final LastAdminGuard lastAdminGuard;
    private final UserTokenService userTokenService;
    private final MailSender mailSender;
    private final MailLinkBuilder mailLinkBuilder;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public Page<UserDirectoryViewResponse> search(String q, List<String> qFields, Pageable pageable) {
        Specification<User> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, qFields, List.of());
        return userDirectoryQueryExecutor.search(applyVisibilityScope(spec), pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /users/search}. */
    @Transactional(readOnly = true)
    public Page<UserDirectoryViewResponse> search(SearchRequest request, Pageable pageable) {
        Specification<User> spec = FilterSpecifications.from(FILTER_FIELDS, request.q(), request.qFields(),
                request.filters());
        return userDirectoryQueryExecutor.search(applyVisibilityScope(spec), pageable);
    }

    // ── visibility scope (iam:user:read vs iam:group-member:read) ──

    /**
     * Narrows a directory spec to row-level visibility: {@code iam:user:read} is
     * unrestricted; {@code iam:group-member:read} sees own-group members + self.
     */
    private Specification<User> applyVisibilityScope(Specification<User> spec) {
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
                cb.or(cb.equal(root.get(BaseEntity_.ID), callerId),
                        visible.isEmpty() ? cb.disjunction() : root.get(BaseEntity_.ID).in(visible)));
    }

    /**
     * Detail-scope guard: 403 {@code auth_access_denied} when the caller (lacking
     * {@code iam:user:read}) is neither the target nor a member of the target's groups.
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
     * Sorted effective permission names for a user (direct + active-group roles +
     * transitive parent inheritance); scope-guarded like the detail endpoint.
     */
    @Transactional(readOnly = true)
    public List<String> effectivePermissions(UUID id) {
        assertViewable(id);
        getUserOrThrow(id);
        return customUserDetailsService.resolveEffectivePermissionNamesForUser(id);
    }

    /**
     * Temporal account-activity summary for the detail view; the login-history lookup
     * is kept OUT of {@code toResponse} so mutation responses don't pay for it.
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
        // No session revoke needed: a brand-new user has no outstanding tokens (404 on unknown ids).
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
        // Optional-policy verify mail: best-effort AFTER commit — creation must never depend on SMTP.
        scheduleVerificationMail(saved);
        return toResponse(saved);
    }

    // ── email verification (optional policy) ──

    /**
     * Consumes an email-verification token and marks the address verified. Idempotent
     * for a re-clicked link whose user is already verified; public (unauthenticated) —
     * tenant scope comes from {@code TenantFilter} (subdomain-anchored link).
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        UserAuthToken token;
        try {
            token = userTokenService.consume(rawToken, UserAuthTokenPurpose.EMAIL_VERIFY);
        } catch (BusinessException e) {
            if (e.errorCode() != ErrorCode.USER_TOKEN_ALREADY_USED
                    || !alreadyVerifiedWithoutConsuming(rawToken)) {
                throw e;
            }
            // Used token + verified user → the first click already did the work.
            return;
        }
        User user = userRepository.findById(token.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for verification token"));
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
            log.info("User email verified: userId={}", user.getId());
        }
    }

    /** Idempotency probe: a consumed EMAIL_VERIFY token whose user is already verified. */
    private boolean alreadyVerifiedWithoutConsuming(String rawToken) {
        return userTokenService.peek(rawToken)
                .filter(t -> t.getPurpose() == UserAuthTokenPurpose.EMAIL_VERIFY && t.isUsed())
                .map(t -> userRepository.findById(t.getUser().getId())
                        .map(User::isEmailVerified)
                        .orElse(false))
                .orElse(false);
    }

    /**
     * Admin-triggered resend; fail-loud (unlike creation's best-effort send): the caller
     * explicitly asked, so an SMTP failure rolls back the tx + fresh token.
     */
    @Transactional
    @AuditLog(action = "user_verification_resent", entityType = "User", entityId = "#id", entityName = "")
    public void resendVerification(UUID id) {
        User user = getUserOrThrow(id);
        if (user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.USER_ALREADY_VERIFIED,
                    "User's email is already verified: " + user.getEmail());
        }
        sendVerificationMail(user);
    }

    /** After-commit wrapper so creation never rolls back on a mail failure. */
    private void scheduleVerificationMail(User user) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendVerificationMailSafe(user);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendVerificationMailSafe(user);
            }
        });
    }

    // ── self-service password reset (forgot-password / reset-password) ──

    /**
     * Forgot-password: ALWAYS returns normally — unknown/disabled address and mail
     * failure are indistinguishable (no account enumeration, no 500-vs-200 leak).
     */
    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.getUserAccount().isEnabled()) {
            log.debug("Password reset requested for unknown/disabled address");
            return;
        }
        try {
            String rawToken = userTokenService.issue(user.getId(), UserAuthTokenPurpose.PASSWORD_RESET);
            Company company = currentCompany();
            String link = mailLinkBuilder.tenantLink(company.getSchemaName(), "/reset-password", rawToken);
            mailSender.send(new MailMessage(
                    user.getEmail(),
                    MailTemplate.PASSWORD_RESET,
                    link,
                    displayName(user),
                    company.getName(),
                    userTokenService.ttl(UserAuthTokenPurpose.PASSWORD_RESET)));
        } catch (Exception e) {
            log.error("Password reset mail failed — request returns silently", e);
        }
    }

    /**
     * Consumes a reset token, applies the new password and kills ALL of the user's
     * sessions (RISK-21/K-34 revoke chain — same as admin reset).
     */
    @Transactional
    @AuditLog(action = "user_password_reset_self", entityType = "User", entityId = "", entityName = "")
    public void resetPasswordWithToken(String rawToken, String newPassword) {
        UserAuthToken token = userTokenService.consume(rawToken, UserAuthTokenPurpose.PASSWORD_RESET);
        User user = userRepository.findById(token.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for reset token"));
        user.setPassword(passwordEncoder.encode(newPassword));
        sessionRevocationService.revokeUser(user.getId());
        userRepository.save(user);
        log.info("Self-service password reset completed: userId={}", user.getId());
    }

    /** Best-effort wrapper: mail failures are logged, never propagated (admin can resend). */
    private void sendVerificationMailSafe(User user) {
        try {
            sendVerificationMail(user);
        } catch (Exception e) {
            log.error("Verification mail failed for user {} — user creation continues; admin can resend",
                    user.getId(), e);
        }
    }

    private void sendVerificationMail(User user) {
        String rawToken = userTokenService.issue(user.getId(), UserAuthTokenPurpose.EMAIL_VERIFY);
        Company company = currentCompany();
        String link = mailLinkBuilder.tenantLink(company.getSchemaName(), "/verify-email", rawToken);
        mailSender.send(new MailMessage(
                user.getEmail(),
                MailTemplate.EMAIL_VERIFY,
                link,
                displayName(user),
                company.getName(),
                userTokenService.ttl(UserAuthTokenPurpose.EMAIL_VERIFY)));
    }

    /** First name when present; the template's greeting falls back gracefully. */
    private String displayName(User user) {
        UserProfile profile = user.getUserProfile();
        return profile == null ? null : profile.getFirstName();
    }

    private Company currentCompany() {
        String schemaName = TenantContext.getCurrentTenant().orElse(null);
        if (schemaName == null || schemaName.isBlank() || "public".equals(schemaName)) {
            throw new IllegalStateException("Verification mail requires an active tenant context");
        }
        return companyRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("No company for tenant schema " + schemaName));
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
        // Guard BEFORE save: the existence query auto-flushes the pending enabled=false,
        // so it sees the post-mutation state.
        if (disabling) {
            lastAdminGuard.assertActiveAdminExists();
        }
        User saved = userRepository.save(user);
        // Disabled user's tokens must die now, not at TTL (the JWT filter does not re-read account flags).
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
        // Self-delete forbidden unconditionally — the historical cause of tenant lockouts.
        lastAdminGuard.assertNotSelf(id);
        userRepository.deleteById(id);
        // Guard AFTER the soft-delete flush: violation rolls back the whole tx and the
        // revoke below never fires (no Redis-side carnage on rejection).
        lastAdminGuard.assertActiveAdminExists();
        sessionRevocationService.revokeUser(id);
    }

    /**
     * Clears an active brute-force lockout ([RISK-22]): resets the counter and
     * {@code lockedUntil}; refresh tokens were left intact by the lock, so no session
     * revive is needed. No-op state-wise when not locked (still audited).
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
        // Guard: stripping an admin-carrying role may drop below the one-active-admin
        // floor (the existence query auto-flushes the join-row removal first).
        lastAdminGuard.assertActiveAdminExists();
        User saved = userRepository.save(user);
        // Kill sessions so the permission delta is enforced immediately, not at access-token TTL.
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
        // Guard: removing the user from an admin-carrying group may drop below the floor (auto-flushed first).
        lastAdminGuard.assertActiveAdminExists();
        User saved = userRepository.save(user);
        // Kill sessions so the permission delta is enforced immediately, not at access-token TTL.
        sessionRevocationService.revokeUser(saved.getId());
        // Faz 2b: set delta values for AOP aspect
        AuditDeltaContext.setOldValue(AuditService.namesJson(beforeNames));
        AuditDeltaContext.setNewValue(AuditService.namesJson(groups.stream().map(Group::getName).collect(java.util.stream.Collectors.toSet())));
        return toResponse(saved);
    }

    /** Self-service profile update; patch semantics (null = unchanged, empty string clears). */
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
     * Self-service change (current password verified); kills ALL outstanding sessions
     * — multi-device logout via {@code tokenInvalidBefore} ([RISK-21]).
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
     * Admin reset — no current password verified (caller holds {@code iam:user:write});
     * same multi-device revoke chain as {@link #changePassword}.
     */
    @Transactional
    @AuditLog(action = "user_password_reset", entityType = "User", entityId = "#userId", entityName = "#user.email")
    public void resetPassword(UUID userId, AdminPasswordResetRequest request) {
        User user = getUserOrThrow(userId);
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        invalidateTokens(user);
        userRepository.save(user);
    }

    /** [RISK-21] Logout hook: stamps {@code tokenInvalidBefore} for the user. */
    @Transactional
    public void revokeTokens(UUID userId) {
        User user = getUserOrThrow(userId);
        invalidateTokens(user);
        userRepository.save(user);
    }

    /**
     * Centralized session revoke ([RISK-21 + K-34 + Faz 1]): stamps
     * {@code tokenInvalidBefore} AND drops refresh tokens — a stolen refresh cannot
     * mint a fresh access token whose {@code iat} post-dates the revoke.
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
