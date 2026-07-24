package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AssignGroupsRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.AdminPasswordResetRequest;
import com.ibrhalil.forgesys.dto.GroupSummary;
import com.ibrhalil.forgesys.dto.PasswordChangeRequest;
import com.ibrhalil.forgesys.dto.RoleSummary;
import com.ibrhalil.forgesys.dto.UserCreateRequest;
import com.ibrhalil.forgesys.dto.UserProfileUpdateRequest;
import com.ibrhalil.forgesys.dto.UserResponse;
import com.ibrhalil.forgesys.dto.UserUpdateRequest;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return toResponse(getUserOrThrow(id));
    }

    @Transactional
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

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = getUserOrThrow(id);
        UserProfile profile = user.getUserProfile();
        if (profile != null) {
            profile.setFirstName(request.firstName());
            profile.setLastName(request.lastName());
        }
        if (request.enabled() != null) {
            user.getUserAccount().setEnabled(request.enabled());
        }
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public UserResponse setRoles(UUID userId, AssignRolesRequest request) {
        User user = getUserOrThrow(userId);
        List<Role> roles = resolveRoles(request.roleIds());
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse setGroups(UUID userId, AssignGroupsRequest request) {
        User user = getUserOrThrow(userId);
        List<Group> groups = resolveGroups(request.groupIds());
        user.getGroups().clear();
        user.getGroups().addAll(groups);
        return toResponse(userRepository.save(user));
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

    private void invalidateTokens(User user) {
        UserAccount account = user.getUserAccount();
        if (account == null) {
            // Defensive: a password write on an account-less user shouldn't happen
            // (login requires an account), but we don't want to NPE here.
            return;
        }
        account.setTokenInvalidBefore(OffsetDateTime.now());
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
