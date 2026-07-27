package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.LoginRequest;
import com.ibrhalil.forgesys.dto.LoginResponse;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.exception.AuthException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String HASH = "$2a$12$hashed";

    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LoginHistoryService loginHistoryService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(passwordEncoder, tokenProvider, userRepository, loginHistoryService);
        TenantContext.setCurrentTenant("tenant_test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void loginSuccessRecordsSuccessAndReturnsToken() {
        UUID userId = UUID.randomUUID();
        User user = userWithAccount(userId, null, 0);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", HASH)).thenReturn(true);
        when(passwordEncoder.upgradeEncoding(HASH)).thenReturn(false);
        when(tokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("tok");
        when(tokenProvider.getAccessTokenTtlMinutes()).thenReturn(5L);

        LoginResponse response = authService.login(new LoginRequest(EMAIL, "secret"));

        assertEquals("tok", response.accessToken());
        assertEquals(userId, response.userId());
        verify(loginHistoryService).record(userId, EMAIL, true, null);
    }

    @Test
    void loginUnknownEmailRecordsFailureWithNullUserId() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("$2a$12$dummy");

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.login(new LoginRequest("ghost@example.com", "secret")));

        assertEquals(ErrorCode.AUTH_BAD_CREDENTIALS, ex.errorCode());
        verify(loginHistoryService).record(null, "ghost@example.com", false, "auth_bad_credentials");
    }

    @Test
    void loginAccountLessUserRecordsFailure() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail(EMAIL);
        user.setPassword(HASH);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.login(new LoginRequest(EMAIL, "secret")));

        assertEquals(ErrorCode.AUTH_BAD_CREDENTIALS, ex.errorCode());
        verify(loginHistoryService).record(userId, EMAIL, false, "auth_bad_credentials");
    }

    @Test
    void loginWrongPasswordRecordsFailureAndIncrementsCounter() {
        UUID userId = UUID.randomUUID();
        User user = userWithAccount(userId, null, 2);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.login(new LoginRequest(EMAIL, "wrong")));

        assertEquals(ErrorCode.AUTH_BAD_CREDENTIALS, ex.errorCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getUserAccount().getFailedLoginAttempts());
        verify(loginHistoryService).record(userId, EMAIL, false, "auth_bad_credentials");
    }

    @Test
    void loginLockedAccountRecordsFailureWithLockedReason() {
        UUID userId = UUID.randomUUID();
        User user = userWithAccount(userId, OffsetDateTime.now().plusMinutes(10), 5);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.login(new LoginRequest(EMAIL, "any")));

        assertEquals(ErrorCode.AUTH_ACCOUNT_LOCKED, ex.errorCode());
        verify(loginHistoryService).record(userId, EMAIL, false, "auth_account_locked");
        // [RISK-22] a locked account never reaches the password compare (no timing/attempt leak)
        verifyNoInteractions(passwordEncoder);
    }

    private User userWithAccount(UUID id, OffsetDateTime lockedUntil, int failedAttempts) {
        User user = new User();
        user.setId(id);
        user.setEmail(EMAIL);
        user.setUsername("user");
        user.setPassword(HASH);
        UserAccount account = new UserAccount();
        account.setLockedUntil(lockedUntil);
        account.setFailedLoginAttempts(failedAttempts);
        user.setUserAccount(account);
        return user;
    }
}
