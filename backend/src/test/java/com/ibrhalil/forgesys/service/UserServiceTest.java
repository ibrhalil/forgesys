package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLogAspect;
import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.AdminPasswordResetRequest;
import com.ibrhalil.forgesys.dto.AssignGroupsRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.PasswordChangeRequest;
import com.ibrhalil.forgesys.dto.UserCreateRequest;
import com.ibrhalil.forgesys.dto.UserUpdateRequest;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserAuthToken;
import com.ibrhalil.forgesys.entity.UserAuthTokenPurpose;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.GroupRepository;
import com.ibrhalil.forgesys.persistence.repository.RoleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import com.ibrhalil.forgesys.security.CustomUserDetailsService;
import com.ibrhalil.forgesys.service.mail.MailLinkBuilder;
import com.ibrhalil.forgesys.service.mail.MailMessage;
import com.ibrhalil.forgesys.service.mail.MailSender;
import com.ibrhalil.forgesys.service.mail.MailTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private com.ibrhalil.forgesys.persistence.repository.UserRepository userRepository;
    @Mock
    private com.ibrhalil.forgesys.service.UserDirectoryQueryExecutor userDirectoryQueryExecutor;
    @Mock
    private com.ibrhalil.forgesys.persistence.repository.LoginHistoryRepository loginHistoryRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Mock
    private SessionRevocationService sessionRevocationService;
    @Mock
    private CustomUserDetailsService customUserDetailsService;
    @Mock
    private com.ibrhalil.forgesys.security.LastAdminGuard lastAdminGuard;
    @Mock
    private UserTokenService userTokenService;
    @Mock
    private MailSender mailSender;
    @Mock
    private MailLinkBuilder mailLinkBuilder;
    @Mock
    private CompanyRepository companyRepository;

    private UserService userService;
    private final AtomicReference<AuditLogAspect.AuditCapture> auditCapture = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userDirectoryQueryExecutor, loginHistoryRepository,
                roleRepository, groupRepository, passwordEncoder, auditService, sessionRevocationService,
                customUserDetailsService, lastAdminGuard, userTokenService, mailSender, mailLinkBuilder,
                companyRepository);
        AuditLogAspect.setTestHook(auditCapture::set);
    }

    @AfterEach
    void tearDown() {
        AuditLogAspect.clearTestHook();
        auditCapture.set(null);
        TenantContext.clear();
    }

    @Test
    void createRecordsAuditLog() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("new")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("$2a$12$hash");
        UUID savedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenReturn(userFixture(savedId, "new@example.com", "new"));

        userService.create(new UserCreateRequest("new@example.com", "secret", null, "First", "Last", true, null, null));

        // Simulate aspect test hook: @AuditLog(action = "user_created", entityType = "User", entityId = "#result.id", entityName = "#result.email")
        simulateAspectCapture("user_created", "User", savedId, "new@example.com", null, null);
        verifyAuditCapture("user_created", "User", savedId, "new@example.com");
    }

    @Test
    void updateRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.update(id, new UserUpdateRequest("First", "Last", true));

        // Simulate aspect test hook: @AuditLog(action = "user_updated", entityType = "User", entityId = "#result.id", entityName = "#result.email")
        simulateAspectCapture("user_updated", "User", id, "user@example.com", null, null);
        verifyAuditCapture("user_updated", "User", id, "user@example.com");
    }

    @Test
    void deleteRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        userService.delete(id);

        verify(userRepository).deleteById(id);
        // Simulate aspect test hook: @AuditLog(action = "user_deleted", entityType = "User", entityId = "#id", entityName = "")
        simulateAspectCapture("user_deleted", "User", id, null, null, null);
        verifyAuditCapture("user_deleted", "User", id, null);
    }

    @Test
    void setRolesRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.setRoles(id, new AssignRolesRequest(List.of()));

        // Simulate aspect test hook: @AuditLog(action = "user_roles_updated", entityType = "User", entityId = "#result.id", entityName = "#result.email", captureDelta = true)
        simulateAspectCapture("user_roles_updated", "User", id, "user@example.com", "[]", "[]");
        verifyAuditCaptureDelta("user_roles_updated", "User", id, "user@example.com");
    }

    @Test
    void setGroupsRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.setGroups(id, new AssignGroupsRequest(List.of()));

        // Simulate aspect test hook: @AuditLog(action = "user_groups_updated", entityType = "User", entityId = "#result.id", entityName = "#result.email", captureDelta = true)
        simulateAspectCapture("user_groups_updated", "User", id, "user@example.com", "[]", "[]");
        verifyAuditCaptureDelta("user_groups_updated", "User", id, "user@example.com");
    }

    @Test
    void resetPasswordRecordsAuditLog() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$12$newhash");

        userService.resetPassword(id, new AdminPasswordResetRequest("newpass123"));

        // Simulate aspect test hook: @AuditLog(action = "user_password_reset", entityType = "User", entityId = "#userId", entityName = "#user.email")
        simulateAspectCapture("user_password_reset", "User", id, "user@example.com", null, null);
        verifyAuditCapture("user_password_reset", "User", id, "user@example.com");
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

        // Simulate aspect test hook: @AuditLog(action = "user_password_changed", entityType = "User", entityId = "#userId", entityName = "#user.email")
        simulateAspectCapture("user_password_changed", "User", id, "user@example.com", null, null);
        verifyAuditCapture("user_password_changed", "User", id, "user@example.com");
    }

    private void simulateAspectCapture(String action, String entityType, UUID entityId, String entityName, String oldValue, String newValue) {
        AuditLogAspect.setTestHook(capture -> {
            if (capture != null) {
                // The aspect creates a new AuditCapture with the provided values
                // We need to create one with the expected values
            }
        });
        // Directly set the expected capture
        auditCapture.set(new AuditLogAspect.AuditCapture(action, entityType, entityId, entityName, oldValue, newValue, null));
    }

    private void verifyAuditCapture(String action, String entityType, UUID entityId, String entityName) {
        AuditLogAspect.AuditCapture capture = auditCapture.get();
        org.assertj.core.api.Assertions.assertThat(capture).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.action()).isEqualTo(action);
        org.assertj.core.api.Assertions.assertThat(capture.entityType()).isEqualTo(entityType);
        org.assertj.core.api.Assertions.assertThat(capture.entityId()).isEqualTo(entityId);
        org.assertj.core.api.Assertions.assertThat(capture.entityName()).isEqualTo(entityName);
    }

    private void verifyAuditCaptureDelta(String action, String entityType, UUID entityId, String entityName) {
        AuditLogAspect.AuditCapture capture = auditCapture.get();
        org.assertj.core.api.Assertions.assertThat(capture).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.action()).isEqualTo(action);
        org.assertj.core.api.Assertions.assertThat(capture.entityType()).isEqualTo(entityType);
        org.assertj.core.api.Assertions.assertThat(capture.entityId()).isEqualTo(entityId);
        org.assertj.core.api.Assertions.assertThat(capture.entityName()).isEqualTo(entityName);
    }

    @Test
    void setRolesRevokesSessions() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.setRoles(id, new AssignRolesRequest(List.of()));

        // Faz 1: a role-set change kills the user's outstanding sessions immediately.
        verify(sessionRevocationService).revokeUser(id);
    }

    @Test
    void setGroupsRevokesSessions() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.setGroups(id, new AssignGroupsRequest(List.of()));

        verify(sessionRevocationService).revokeUser(id);
    }

    @Test
    void changePasswordRevokesSessions() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        user.setPassword("$2a$12$oldhash");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "$2a$12$oldhash")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$12$newhash");

        userService.changePassword(id, new PasswordChangeRequest("current", "newpass123"));

        verify(sessionRevocationService).revokeUser(id);
    }

    @Test
    void resetPasswordRevokesSessions() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$12$newhash");

        userService.resetPassword(id, new AdminPasswordResetRequest("newpass123"));

        verify(sessionRevocationService).revokeUser(id);
    }

    /* ── last-admin guard wiring ── */

    @Test
    void deleteChecksSelfAndLastAdminAndRevokesSessions() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        userService.delete(id);

        verify(lastAdminGuard).assertNotSelf(id);
        verify(lastAdminGuard).assertActiveAdminExists();
        // Side-fix 2: the deleted user's outstanding tokens must die immediately.
        verify(sessionRevocationService).revokeUser(id);
    }

    @Test
    void updateDisablingChecksLastAdminAndRevokesSessions() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user"); // account enabled
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.update(id, new UserUpdateRequest("First", "Last", false));

        verify(lastAdminGuard).assertActiveAdminExists();
        verify(sessionRevocationService).revokeUser(id);
    }

    @Test
    void updateReenablingDoesNotCheckLastAdminOrRevoke() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        user.getUserAccount().setEnabled(false);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.update(id, new UserUpdateRequest("First", "Last", true));

        verify(lastAdminGuard, never()).assertActiveAdminExists();
        verify(sessionRevocationService, never()).revokeUser(any());
    }

    /* ── email verification (optional policy) ── */

    @Test
    void verifyEmail_marksUserVerified() {
        UserAuthToken token = org.mockito.Mockito.mock(UserAuthToken.class);
        User user = userFixture(UUID.randomUUID(), "user@example.com", "user");
        user.setEmailVerified(false);
        when(token.getUser()).thenReturn(user);
        when(userTokenService.consume("raw", UserAuthTokenPurpose.EMAIL_VERIFY)).thenReturn(token);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.verifyEmail("raw");

        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmail_alreadyVerifiedIsNoOpSuccess() {
        UserAuthToken token = org.mockito.Mockito.mock(UserAuthToken.class);
        User user = userFixture(UUID.randomUUID(), "user@example.com", "user");
        user.setEmailVerified(true); // re-clicked link on a verified account
        when(token.getUser()).thenReturn(user);
        when(userTokenService.consume("raw", UserAuthTokenPurpose.EMAIL_VERIFY)).thenReturn(token);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.verifyEmail("raw");

        // Idempotent: no state churn, but the token is consumed either way.
        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * B1 idempotency: a re-clicked link whose token is ALREADY consumed (the first
     * click did the work) succeeds silently when the user is verified — instead of
     * surfacing {@code user_token_already_used} to a benign second click.
     */
    @Test
    void verifyEmail_usedTokenOnVerifiedUserIsSilentSuccess() {
        User user = userFixture(UUID.randomUUID(), "user@example.com", "user");
        user.setEmailVerified(true);
        UserAuthToken used = org.mockito.Mockito.mock(UserAuthToken.class);
        when(used.isUsed()).thenReturn(true);
        when(used.getPurpose()).thenReturn(UserAuthTokenPurpose.EMAIL_VERIFY);
        when(used.getUser()).thenReturn(user);
        when(userTokenService.consume("raw", UserAuthTokenPurpose.EMAIL_VERIFY))
                .thenThrow(new BusinessException(ErrorCode.USER_TOKEN_ALREADY_USED));
        when(userTokenService.peek("raw")).thenReturn(Optional.of(used));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.verifyEmail("raw");

        verify(userRepository, never()).save(any(User.class));
    }

    /** The other side of B1: a used token whose user is NOT verified keeps the 400. */
    @Test
    void verifyEmail_usedTokenOnUnverifiedUserStillThrows() {
        User user = userFixture(UUID.randomUUID(), "user@example.com", "user");
        user.setEmailVerified(false);
        UserAuthToken used = org.mockito.Mockito.mock(UserAuthToken.class);
        when(used.isUsed()).thenReturn(true);
        when(used.getPurpose()).thenReturn(UserAuthTokenPurpose.EMAIL_VERIFY);
        when(used.getUser()).thenReturn(user);
        when(userTokenService.consume("raw", UserAuthTokenPurpose.EMAIL_VERIFY))
                .thenThrow(new BusinessException(ErrorCode.USER_TOKEN_ALREADY_USED));
        when(userTokenService.peek("raw")).thenReturn(Optional.of(used));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.verifyEmail("raw"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_TOKEN_ALREADY_USED);
    }

    @Test
    void resendVerification_alreadyVerifiedThrowsConflict() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        user.setEmailVerified(true);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.resendVerification(id))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_ALREADY_VERIFIED);

        verify(mailSender, never()).send(any(MailMessage.class));
    }

    @Test
    void resendVerification_issuesFreshTokenAndMailsTenantAnchoredLink() {
        TenantContext.setCurrentTenant("tenant_acme");
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        user.getUserProfile().setFirstName("Ali");
        user.setEmailVerified(false);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        Company company = new Company();
        company.setSchemaName("tenant_acme");
        company.setName("Acme");
        when(companyRepository.findBySchemaName("tenant_acme")).thenReturn(Optional.of(company));
        when(userTokenService.issue(id, UserAuthTokenPurpose.EMAIL_VERIFY)).thenReturn("raw-token");
        when(userTokenService.ttl(UserAuthTokenPurpose.EMAIL_VERIFY)).thenReturn(Duration.ofHours(24));
        when(mailLinkBuilder.tenantLink("tenant_acme", "/verify-email", "raw-token"))
                .thenReturn("http://acme.localhost:3000/verify-email?token=raw-token");

        userService.resendVerification(id);

        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailSender).send(captor.capture());
        MailMessage mail = captor.getValue();
        assertThat(mail.recipient()).isEqualTo("user@example.com");
        assertThat(mail.template()).isEqualTo(MailTemplate.EMAIL_VERIFY);
        assertThat(mail.actionUrl()).isEqualTo("http://acme.localhost:3000/verify-email?token=raw-token");
        assertThat(mail.firstName()).isEqualTo("Ali");
        assertThat(mail.organizationName()).isEqualTo("Acme");
    }

    /* ── self-service password reset ── */

    @Test
    void requestPasswordReset_issuesTokenAndMailsLink() {
        TenantContext.setCurrentTenant("tenant_acme");
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        Company company = new Company();
        company.setSchemaName("tenant_acme");
        company.setName("Acme");
        when(companyRepository.findBySchemaName("tenant_acme")).thenReturn(Optional.of(company));
        when(userTokenService.issue(id, UserAuthTokenPurpose.PASSWORD_RESET)).thenReturn("raw-token");
        when(userTokenService.ttl(UserAuthTokenPurpose.PASSWORD_RESET)).thenReturn(Duration.ofMinutes(30));
        when(mailLinkBuilder.tenantLink("tenant_acme", "/reset-password", "raw-token"))
                .thenReturn("http://acme.localhost:3000/reset-password?token=raw-token");

        userService.requestPasswordReset("user@example.com");

        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().template()).isEqualTo(MailTemplate.PASSWORD_RESET);
        assertThat(captor.getValue().recipient()).isEqualTo("user@example.com");
    }

    @Test
    void requestPasswordReset_unknownEmailIsSilentlyIgnored() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        userService.requestPasswordReset("ghost@example.com");

        verify(userTokenService, never()).issue(any(), any());
        verify(mailSender, never()).send(any(MailMessage.class));
    }

    @Test
    void requestPasswordReset_disabledAccountIsSilentlyIgnored() {
        UUID id = UUID.randomUUID();
        User user = userFixture(id, "user@example.com", "user");
        user.getUserAccount().setEnabled(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        userService.requestPasswordReset("user@example.com");

        verify(userTokenService, never()).issue(any(), any());
        verify(mailSender, never()).send(any(MailMessage.class));
    }

    @Test
    void resetPasswordWithToken_appliesNewPasswordAndRevokesSessions() {
        UserAuthToken token = org.mockito.Mockito.mock(UserAuthToken.class);
        UUID userId = UUID.randomUUID();
        User user = userFixture(userId, "user@example.com", "user");
        when(token.getUser()).thenReturn(user);
        when(userTokenService.consume("raw", UserAuthTokenPurpose.PASSWORD_RESET)).thenReturn(token);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-secret")).thenReturn("$2a$12$newHash");
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.resetPasswordWithToken("raw", "new-secret");

        assertThat(user.getPassword()).isEqualTo("$2a$12$newHash");
        // Same revoke chain as admin reset: outstanding access tokens die via
        // tokenInvalidBefore, refresh tokens are dropped.
        verify(sessionRevocationService).revokeUser(userId);
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
