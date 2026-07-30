package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the last-admin invariant guard: the admin-capable role-closure
 * computation (flag roles + downward inheritance) and the self-delete check.
 */
@ExtendWith(MockitoExtension.class)
class LastAdminGuardTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRepository userRepository;

    private LastAdminGuard guard;

    @BeforeEach
    void setUp() {
        guard = new LastAdminGuard(roleRepository, userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId) {
        CustomUserDetails principal = new CustomUserDetails(
                userId, "actor@example.com", null, true, true, true, true,
                java.util.Set.of(), null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }

    private Role flagRole(UUID id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setAllPermissions(true);
        return role;
    }

    /* ── assertActiveAdminExists ── */

    @Test
    void passesWhenEnabledAdminHolderRemains() {
        UUID adminRoleId = UUID.randomUUID();
        when(roleRepository.findAllByAllPermissionsTrue()).thenReturn(List.of(flagRole(adminRoleId, "Admin")));
        when(userRepository.existsEnabledByRoleIds(anyCollection())).thenReturn(true);

        assertThatCode(() -> guard.assertActiveAdminExists()).doesNotThrowAnyException();
    }

    @Test
    void throwsWhenNoFlagRoleExists() {
        when(roleRepository.findAllByAllPermissionsTrue()).thenReturn(List.of());

        assertThatThrownBy(() -> guard.assertActiveAdminExists())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.LAST_ADMIN_REQUIRED);
    }

    @Test
    void throwsWhenFlagRolesHaveNoEnabledHolder() {
        UUID adminRoleId = UUID.randomUUID();
        when(roleRepository.findAllByAllPermissionsTrue()).thenReturn(List.of(flagRole(adminRoleId, "Admin")));
        when(userRepository.existsEnabledByRoleIds(anyCollection())).thenReturn(false);

        assertThatThrownBy(() -> guard.assertActiveAdminExists())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.LAST_ADMIN_REQUIRED);
    }

    @Test
    void closureIncludesRolesInheritingFromAdminRole() {
        UUID adminRoleId = UUID.randomUUID();
        UUID childRoleId = UUID.randomUUID();
        when(roleRepository.findAllByAllPermissionsTrue()).thenReturn(List.of(flagRole(adminRoleId, "Admin")));
        when(userRepository.existsEnabledByRoleIds(anyCollection())).thenReturn(true);
        // Child inherits from Admin -> admin-capable too; a second hop adds nothing new.
        when(roleRepository.findChildRoleIds(anyCollection()))
                .thenAnswer(inv -> {
                    Collection<UUID> parents = inv.getArgument(0);
                    return parents.contains(adminRoleId) ? List.of(childRoleId) : List.of();
                })
                .thenReturn(List.of());

        guard.assertActiveAdminExists();

        // The holder check must consider BOTH the flag role and its inheriting child.
        assertThat(capturedRoleIds()).containsExactlyInAnyOrder(adminRoleId, childRoleId);
    }

    @Test
    void closureTerminatesOnCyclicInheritance() {
        UUID adminRoleId = UUID.randomUUID();
        when(roleRepository.findAllByAllPermissionsTrue()).thenReturn(List.of(flagRole(adminRoleId, "Admin")));
        when(userRepository.existsEnabledByRoleIds(anyCollection())).thenReturn(true);
        // Malformed cycle: the admin role appears as its own (transitive) child — the
        // visited set must stop the traversal, not loop forever.
        when(roleRepository.findChildRoleIds(anyCollection())).thenReturn(List.of(adminRoleId));

        assertThatCode(() -> guard.assertActiveAdminExists()).doesNotThrowAnyException();
        assertThat(capturedRoleIds()).containsExactly(adminRoleId);
    }

    /** Role ids passed to the holder-existence check by the last {@code assertActiveAdminExists}. */
    @SuppressWarnings("unchecked")
    private Set<UUID> capturedRoleIds() {
        org.mockito.ArgumentCaptor<Collection<UUID>> captor =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
        org.mockito.Mockito.verify(userRepository).existsEnabledByRoleIds(captor.capture());
        return captor.getValue().stream().collect(Collectors.toSet());
    }

    /* ── assertNotSelf ── */

    @Test
    void selfDeleteIsRejected() {
        UUID actorId = UUID.randomUUID();
        authenticateAs(actorId);

        assertThatThrownBy(() -> guard.assertNotSelf(actorId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.SELF_DELETE_FORBIDDEN);
    }

    @Test
    void deletingAnotherUserIsAllowed() {
        authenticateAs(UUID.randomUUID());

        assertThatCode(() -> guard.assertNotSelf(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void selfDeleteCheckIsNoOpWhenUnauthenticated() {
        // Startup/system paths (no SecurityContext principal) must not trip the check.
        UUID anyId = UUID.randomUUID();

        assertThatCode(() -> guard.assertNotSelf(anyId)).doesNotThrowAnyException();
    }
}
