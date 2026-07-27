package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.UserCreateRequest;
import com.ibrhalil.forgesys.dto.UserUpdateRequest;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleRepository, groupRepository, passwordEncoder, auditService);
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
