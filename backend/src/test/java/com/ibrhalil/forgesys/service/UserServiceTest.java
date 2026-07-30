package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AdminPasswordResetRequest;
import com.ibrhalil.forgesys.dto.AssignGroupsRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.PasswordChangeRequest;
import com.ibrhalil.forgesys.dto.UserCreateRequest;
import com.ibrhalil.forgesys.dto.UserUpdateRequest;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.refresh.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Mock
    private RefreshTokenStore refreshTokenStore;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleRepository, groupRepository, passwordEncoder, auditService, refreshTokenStore);
    }

    @Test
    void createRecordsAuditLog() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("new")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("$2a$12$hash");
        UUID savedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenReturn(userFixture(savedId, "new@example.com", "new"));

        userService.create(new UserCreateRequest("new@example.com", "secret", null, "First", "Last", true));

        verify(auditService).record("user_created", "User", savedId, "new@example.com");
    }

    @Test
    void updateRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.update(id, new UserUpdateRequest("First", "Last", true));

        verify(auditService).record("user_updated", "User", id, "user@example.com");
    }

    @Test
    void deleteRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        userService.delete(id);

        verify(userRepository).deleteById(id);
        verify(auditService).record("user_deleted", "User", id, null);
    }

    @Test
    void setRolesRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.setRoles(id, new AssignRolesRequest(List.of()));

        verify(auditService).record("user_roles_updated", "User", id, "user@example.com");
    }

    @Test
    void setGroupsRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.setGroups(id, new AssignGroupsRequest(List.of()));

        verify(auditService).record("user_groups_updated", "User", id, "user@example.com");
    }

    @Test
    void resetPasswordRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$12$newhash");

        userService.resetPassword(id, new AdminPasswordResetRequest("newpass123"));

        verify(auditService).record("user_password_reset", "User", id, "user@example.com");
    }

    @Test
    void changePasswordRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        user.setPassword("$2a$12$oldhash");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "$2a$12$oldhash")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$12$newhash");

        userService.changePassword(id, new PasswordChangeRequest("current", "newpass123"));

        verify(auditService).record("user_password_changed", "User", id, "user@example.com");
    }

    private User userFixture(UUID id, String email, String username) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(username);
        UserAccount account = new UserAccount();
        account.setEnabled(true);
        user.setUserAccount(account);
        user.setUserProfile(new UserProfile());
        return user;
    }
}
